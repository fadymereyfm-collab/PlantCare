package com.example.plantcare

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.plantcare.data.repository.WeatherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * WorkManager Worker that fetches current weather and adjusts
 * upcoming watering reminders accordingly.
 *
 * Runs every ~12 hours. Uses coarse location to get weather,
 * then shifts reminder dates forward (rain/cold) or backward (heat).
 *
 * The adjustment is stored in SharedPreferences so notifications
 * can also display weather tips.
 */
class WeatherAdjustmentWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "WeatherAdjustWorker"
        private const val PREFS_NAME = "weather_prefs"
        private const val KEY_LAST_TIP = "last_weather_tip"
        private const val KEY_LAST_EMOJI = "last_weather_emoji"
        private const val KEY_LAST_FACTOR = "last_adjustment_factor"
        private const val KEY_LAST_TEMP = "last_temp"
        private const val KEY_LAST_CITY = "last_city"
        private const val KEY_LAST_DESCRIPTION = "last_weather_description"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val context = applicationContext

            // Check location permission
            val hasCoarse = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasCoarse) {
                Log.d(TAG, "No location permission — skipping weather check")
                return@withContext Result.success()
            }

            // Get last known location
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val location = try {
                locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    ?: locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException getting location", e)
                null
            }

            if (location == null) {
                Log.d(TAG, "No location available — skipping weather check")
                return@withContext Result.success()
            }

            // Fetch current weather (always — needed for the WeatherTipCard UI
            // which displays "right now" temp + city, even when forecast drives
            // the reminder shifts).
            val weatherRepo = WeatherRepository.getInstance(context)
            val weather = weatherRepo.fetchWeather(location.latitude, location.longitude)

            if (weather == null) {
                Log.d(TAG, "Weather fetch failed — skipping")
                return@withContext Result.retry()
            }

            // F7: try the 5-day forecast first — it averages 72h conditions and
            // catches "rain coming tomorrow" that current-weather misses entirely.
            // Fall back to the snapshot-based advice if forecast fails (network,
            // rate limit, parse error) so the worker never silently no-ops.
            val forecast = weatherRepo.fetchForecast(location.latitude, location.longitude)
            val forecastAdvice = forecast?.let { weatherRepo.getForecastBasedAdvice(it) }
            val advice = forecastAdvice ?: weatherRepo.getWateringAdvice(weather)
            val adviceSource = if (forecastAdvice != null) "forecast-72h" else "current-snapshot"

            // Save weather info for UI display
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_LAST_TIP, advice.tipMessage)
                .putString(KEY_LAST_EMOJI, advice.iconEmoji)
                .putFloat(KEY_LAST_FACTOR, advice.adjustmentFactor)
                .putFloat(KEY_LAST_TEMP, (weather.main?.temp ?: 0.0).toFloat())
                .putString(KEY_LAST_CITY, weather.name ?: "")
                .putString(
                    KEY_LAST_DESCRIPTION,
                    weather.weather?.firstOrNull()?.description ?: ""
                )
                .apply()

            // Adjust upcoming reminders if factor != 1.0
            val shiftedCount = if (advice.adjustmentFactor != 1.0f) {
                adjustReminders(context, advice.adjustmentFactor)
            } else 0

            // F8: notify the user when at least one reminder actually shifted, so the
            // change in their calendar isn't silent. One summary notification per run.
            if (shiftedCount > 0) {
                val dayShift = computeDayShift(advice.adjustmentFactor)
                val description = weather.weather?.firstOrNull()?.description
                    ?: advice.tipMessage
                PlantNotificationHelper.showWeatherShiftNotification(
                    context,
                    shiftedCount,
                    dayShift,
                    description
                )
            }

            Log.d(TAG, "Weather check done: ${weather.name}, ${weather.main?.temp}°C, source=$adviceSource, factor=${advice.adjustmentFactor}, shifted=$shiftedCount")
            Result.success()
        } catch (c: kotlinx.coroutines.CancellationException) {
            // #8 fix: WorkManager stops the worker via coroutine
            // cancellation (battery saver, system constraints). Pre-fix
            // the generic catch (Exception) downgraded that into
            // Result.retry(), telling WorkManager to re-schedule a
            // job that should have been quietly stopped — silent
            // battery drain. Re-throw cooperates with the stop signal.
            throw c
        } catch (e: Exception) {
            Log.e(TAG, "Error in weather worker", e)
            Result.retry()
        }
    }

    /**
     * Adjusts future (undone) reminders within the next 7 days based on the weather
     * factor. Returns the number of reminder rows actually shifted (Functional
     * Report §2.3).
     *
     * Differences vs the pre-F6 behaviour:
     *  - graduated thresholds (rain → up to 3 days postponement, heat → up to 2 days
     *    advance) instead of a flat ±1 day,
     *  - all upcoming reminders within 7 days are touched, not just the first per
     *    plant — a plant with three reminders next week was previously shifting only
     *    the nearest one,
     *  - returns count so the caller can post a single summary notification.
     */
    private suspend fun adjustReminders(context: Context, factor: Float): Int {
        val userEmail = EmailContext.current(context) ?: return 0
        val dayShift = computeDayShift(factor)
        if (dayShift == 0) return 0

        // Vacation mode: don't shift reminders the user has consciously
        // told us to ignore. Otherwise the user comes back from holiday
        // to a watering calendar that's been silently slid by 3 days
        // because of weather they were never around for. The PlantReminder
        // worker already gates notifications on vacation; the schedule
        // itself was the missing piece.
        if (com.example.plantcare.feature.vacation.VacationPrefs
                .isVacationActive(context, userEmail, java.time.LocalDate.now())) {
            return 0
        }

        val reminderRepo = com.example.plantcare.data.repository.ReminderRepository.getInstance(context)
        // Locale.US for the wire format — Locale.getDefault() emits
        // Eastern-Arabic digits on ar/fa devices and the SQL date
        // comparisons never match the Latin-digit rows we write
        // elsewhere. Same root cause as the A2 fix in PlantReminderWorker.
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, 1) // start from tomorrow
        val tomorrowStr = sdf.format(cal.time)

        // Limit shifts to the visible week — beyond that the reminder is not yet
        // confirmed in the user's mental schedule and forecasts are unreliable.
        val weekEndCal = Calendar.getInstance()
        weekEndCal.add(Calendar.DAY_OF_YEAR, 7)
        val weekEndStr = sdf.format(weekEndCal.time)

        val futureReminders = reminderRepo.getAllRemindersForUserList(userEmail)
            .filter {
                val date = it.date
                !it.done && date != null && date in tomorrowStr..weekEndStr
            }

        if (futureReminders.isEmpty()) return 0

        val todayStr = sdf.format(Date())
        var shifted = 0

        for (reminder in futureReminders) {
            val date = reminder.date ?: continue
            try {
                val reminderDate = sdf.parse(date) ?: continue
                val reminderCal = Calendar.getInstance().apply { time = reminderDate }
                reminderCal.add(Calendar.DAY_OF_YEAR, dayShift)
                val newDate = sdf.format(reminderCal.time)

                // Never shift below today — pulling a future reminder into the past
                // would either silently drop it or make it overdue without action.
                if (newDate < todayStr) continue
                if (newDate == date) continue // no-op safety

                reminder.date = newDate
                reminderRepo.updateBlocking(reminder)
                shifted++
                Log.d(TAG, "Shifted reminder ${reminder.id} (${reminder.plantName}) by $dayShift day(s) → $newDate")
            } catch (e: Exception) {
                Log.e(TAG, "Error adjusting reminder ${reminder.id}", e)
            }
        }
        return shifted
    }

    /**
     * Graduated mapping from weather adjustment factor to day shift. Tuned so a
     * mild drizzle (factor ≈ 0.9) already pushes by one day, while a heavy rain or
     * heatwave can shift by 2-3 days. Mirrors the recommendation in Functional
     * Report §2.3.
     */
    private fun computeDayShift(factor: Float): Int = when {
        factor <= 0.5f -> 3   // heavy rain / very cold → postpone 3 days
        factor <= 0.7f -> 2
        factor <= 0.9f -> 1
        factor >= 1.5f -> -2  // heatwave → advance 2 days
        factor >= 1.2f -> -1
        else -> 0
    }
}
