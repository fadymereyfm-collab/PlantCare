package com.example.plantcare.feature.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.plantcare.CrashReporter
import com.example.plantcare.EmailContext
import com.example.plantcare.FirebaseSyncManager
import com.example.plantcare.Plant
import com.example.plantcare.ReminderUtils
import com.example.plantcare.WateringReminder
import com.example.plantcare.data.repository.PlantRepository
import com.example.plantcare.data.repository.ReminderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Rolling reminder generator. ReminderUtils.generateReminders only emits
 * reminders for the next [ReminderUtils.GENERATION_WINDOW_DAYS] days, so a
 * plant added 5 months ago would have 0 future reminders left despite
 * being on a perfectly healthy 14-day cycle.
 *
 * This worker runs daily, looks at every user plant with a positive
 * `wateringInterval`, finds the **latest auto reminder** that already
 * exists for the plant, and walks forward by interval until the
 * 180-day horizon — inserting only the dates beyond what's already
 * stored.
 *
 * The "extend from latest" approach is deliberate. The previous
 * "compute expected dates from startDate, insert any missing" logic
 * could generate duplicates: if WeatherAdjustmentWorker had shifted
 * `Mon-29` to `Wed-31`, the worker would treat `Mon-29` as missing
 * and re-insert it, leaving the user with both reminders for the
 * same watering. By extending from the actual latest stored date we
 * preserve weather-shifted schedules untouched.
 *
 * Idempotent — the daily cadence makes re-runs essentially free when
 * the plant is already topped-up.
 *
 * Scheduled in App.scheduleReminderTopUpWorker.
 */
class ReminderTopUpWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val ctx = applicationContext
            val email = EmailContext.current(ctx) ?: return@withContext Result.success()

            val plantRepo = PlantRepository.getInstance(ctx)
            val reminderRepo = ReminderRepository.getInstance(ctx)

            val plants = plantRepo.getAllUserPlantsForUserBlocking(email)
                .filter { it.wateringInterval > 0 && it.startDate != null }
            if (plants.isEmpty()) return@withContext Result.success()

            // Locale.US for the wire format used in SQLite queries and
            // reminder rows. Locale.getDefault() on ar/fa devices emits
            // Eastern-Arabic digits which never match Latin-digit rows.
            // Same A2 fix as PlantReminderWorker.
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val horizonCal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, ReminderUtils.GENERATION_WINDOW_DAYS)
            }
            val horizonStr = sdf.format(horizonCal.time)
            val todayStr = sdf.format(Date())

            var inserted = 0
            for (plant in plants) {
                inserted += topUpPlant(plant, todayStr, horizonStr, sdf, reminderRepo)
            }
            if (inserted > 0) com.example.plantcare.DataChangeNotifier.notifyChange()

            // Piggyback identification cache cleanup onto the daily worker
            // — keeps the cache from growing unbounded without scheduling a
            // separate job. Same 7-day TTL as `PlantIdentificationRepository`
            // honours on read, so this just removes rows that would never
            // produce a cache hit anyway.
            try {
                val cutoff = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
                com.example.plantcare.AppDatabase.getInstance(ctx)
                    .identificationCacheDao()
                    .deleteOlderThan(cutoff)
            } catch (t: Throwable) {
                CrashReporter.log(t)
            }

            Result.success()
        } catch (t: Throwable) {
            CrashReporter.log(t)
            Result.retry()
        }
    }

    /**
     * Compute the next reminder date for this plant and insert all
     * missing future dates up to horizon.
     *
     * Anchor logic:
     *  - If the plant has any AUTO reminder (description empty + repeat
     *    parses to a positive int), start from `latestAutoDate +
     *    interval`. This preserves any weather-shifted dates already
     *    in the table — we don't re-insert their original counterparts.
     *  - If no AUTO reminders exist (fresh plant or all manual), fall
     *    back to `plant.startDate`, fast-forwarded to today so we don't
     *    backfill historical dates.
     *
     * Each insert is wrapped in try/catch because the plant could be
     * deleted between our read and our write — Plant→WateringReminder
     * has FK CASCADE, so a freshly orphaned row would violate the FK
     * constraint.
     */
    private fun topUpPlant(
        plant: Plant,
        todayStr: String,
        horizonStr: String,
        sdf: SimpleDateFormat,
        reminderRepo: ReminderRepository
    ): Int {
        val all = reminderRepo.getRemindersForPlantBlocking(plant.id)
        val latestAutoDate = all
            .filter { isAutoReminder(it) }
            .mapNotNull { it.date }
            .maxOrNull()

        val nextCal = Calendar.getInstance()
        val interval = plant.wateringInterval
        if (latestAutoDate != null) {
            val parsed = try { sdf.parse(latestAutoDate) } catch (_: Throwable) { null }
            if (parsed == null) {
                // Bad date string in DB — fall back to startDate path
                // rather than starting at "now" and risk duplicating
                // legitimate reminders.
                nextCal.time = plant.startDate!!
            } else {
                nextCal.time = parsed
                nextCal.add(Calendar.DAY_OF_YEAR, interval)
            }
        } else {
            nextCal.time = plant.startDate!!
        }

        // Fast-forward to today so a long-dormant plant doesn't backfill
        // historical reminders. The first iteration of the main loop
        // emits the next future watering date.
        while (sdf.format(nextCal.time) < todayStr) {
            nextCal.add(Calendar.DAY_OF_YEAR, interval)
        }

        var inserted = 0
        while (sdf.format(nextCal.time) <= horizonStr) {
            val dateStr = sdf.format(nextCal.time)
            val r = WateringReminder().apply {
                plantId = plant.id
                plantName = plant.nickname ?: plant.name
                date = dateStr
                done = false
                repeat = interval.toString()
                description = ""
                userEmail = plant.userEmail
            }
            try {
                reminderRepo.insertBlocking(r)
                inserted++
                try { FirebaseSyncManager.get().syncReminder(r) }
                catch (t: Throwable) { CrashReporter.log(t) }
            } catch (t: Throwable) {
                // FK violation (plant deleted mid-run) or DB error —
                // log + skip this date. The next worker run will retry
                // from a fresh `existing` snapshot if the plant came
                // back, or skip the plant entirely if it really is gone.
                CrashReporter.log(t)
            }
            nextCal.add(Calendar.DAY_OF_YEAR, interval)
        }
        return inserted
    }

    /**
     * Auto reminders are the ones the watering-interval pipeline emits:
     * empty description + a `repeat` field that parses to a positive
     * int. Manual reminders carry a non-empty description or a "0"
     * repeat. We only use AUTO ones to anchor the next-date computation
     * so a one-off "fertilizer reminder" doesn't push us forward by an
     * unrelated cadence.
     */
    private fun isAutoReminder(r: WateringReminder): Boolean {
        val desc = r.description
        if (!desc.isNullOrBlank()) return false
        val repeatInt = r.repeat?.toIntOrNull() ?: return false
        return repeatInt > 0
    }
}
