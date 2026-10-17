package com.example.plantcare.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.plantcare.BuildConfig
import com.example.plantcare.data.weather.ForecastResponse
import com.example.plantcare.data.weather.WeatherResponse
import com.example.plantcare.data.weather.WeatherService
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for weather data access.
 * Handles fetching weather data from OpenWeatherMap API and caching results locally.
 * Uses SharedPreferences for caching with a 3-hour cache validity period.
 */
class WeatherRepository private constructor(appContext: Context) {

    // Sprint-3 cleanup 2026-05-05: applicationContext only — same pattern
    // as the other Repos to avoid Activity-leak via the singleton.
    private val context: Context = appContext.applicationContext
    private val weatherService: WeatherService = WeatherService.create()
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    /**
     * Fetch current weather for a given location.
     * Uses cached data if available and not expired, otherwise makes API call.
     *
     * @param lat Latitude of the location
     * @param lon Longitude of the location
     * @return WeatherResponse or null if the request fails
     */
    suspend fun fetchWeather(lat: Double, lon: Double): WeatherResponse? =
        withContext(Dispatchers.IO) {
            try {
                // Check if we have valid cached data
                val cachedData = getCachedWeather(lat, lon)
                if (cachedData != null) {
                    return@withContext cachedData
                }

                // Fetch from API
                val response = weatherService.getCurrentWeather(
                    lat = lat,
                    lon = lon,
                    apiKey = OPENWEATHERMAP_API_KEY,
                    units = "metric",
                    lang = "de"
                )

                // Cache the result
                cacheWeather(response, lat, lon)

                response
            } catch (e: Exception) {
                com.example.plantcare.CrashReporter.log(e)
                null
            }
        }

    /**
     * Get cached weather data if it exists and is not expired.
     *
     * @param lat Latitude to validate against cached location
     * @param lon Longitude to validate against cached location
     * @return Cached WeatherResponse or null if not available/expired
     */
    private fun getCachedWeather(lat: Double, lon: Double): WeatherResponse? {
        val cachedJson = sharedPreferences.getString(PREFS_WEATHER_KEY, null) ?: return null
        val cachedTimestamp = sharedPreferences.getLong(PREFS_TIMESTAMP_KEY, 0L)
        val cachedLocation = sharedPreferences.getString(PREFS_LOCATION_KEY, null) ?: return null

        // Check if cache has expired
        if (System.currentTimeMillis() - cachedTimestamp > CACHE_DURATION_MS) {
            return null
        }

        // Check if location matches
        val expectedLocation = cacheKey(lat, lon)
        if (cachedLocation != expectedLocation) {
            return null
        }

        return try {
            gson.fromJson(cachedJson, WeatherResponse::class.java)
        } catch (e: Exception) {
            com.example.plantcare.CrashReporter.log(e)
            null
        }
    }

    /**
     * Cache weather response in SharedPreferences.
     *
     * @param weather WeatherResponse to cache
     * @param lat Latitude of the location
     * @param lon Longitude of the location
     */
    private fun cacheWeather(weather: WeatherResponse, lat: Double, lon: Double) {
        try {
            val weatherJson = gson.toJson(weather)
            sharedPreferences.edit().apply {
                putString(PREFS_WEATHER_KEY, weatherJson)
                putLong(PREFS_TIMESTAMP_KEY, System.currentTimeMillis())
                putString(PREFS_LOCATION_KEY, cacheKey(lat, lon))
                apply()
            }
        } catch (e: Exception) {
            com.example.plantcare.CrashReporter.log(e)
        }
    }

    /**
     * Fetch the 5-day / 3-hour forecast for a given location, with a 12-hour
     * cache (forecast data is refreshed by OpenWeather every 3h server-side
     * but doesn't materially change for our day-shift decisions within a 12h
     * window). Returns null on network/parse failure — callers should fall
     * back to current-weather advice in that case.
     *
     * Functional Report §2.3 / task F7.
     */
    suspend fun fetchForecast(lat: Double, lon: Double): ForecastResponse? =
        withContext(Dispatchers.IO) {
            try {
                val cached = getCachedForecast(lat, lon)
                if (cached != null) return@withContext cached

                val response = weatherService.getForecast(
                    lat = lat,
                    lon = lon,
                    apiKey = OPENWEATHERMAP_API_KEY,
                    units = "metric",
                    lang = "de"
                )

                cacheForecast(response, lat, lon)
                response
            } catch (e: Exception) {
                com.example.plantcare.CrashReporter.log(e)
                null
            }
        }

    private fun getCachedForecast(lat: Double, lon: Double): ForecastResponse? {
        val cachedJson = sharedPreferences.getString(PREFS_FORECAST_KEY, null) ?: return null
        val cachedTimestamp = sharedPreferences.getLong(PREFS_FORECAST_TIMESTAMP_KEY, 0L)
        val cachedLocation = sharedPreferences.getString(PREFS_FORECAST_LOCATION_KEY, null) ?: return null

        if (System.currentTimeMillis() - cachedTimestamp > FORECAST_CACHE_DURATION_MS) return null
        if (cachedLocation != cacheKey(lat, lon)) return null

        return try {
            gson.fromJson(cachedJson, ForecastResponse::class.java)
        } catch (e: Exception) {
            com.example.plantcare.CrashReporter.log(e)
            null
        }
    }

    /**
     * Round to 2 decimals (~1.1 km grid) and use Locale.US so the comma-vs-
     * dot decimal separator is stable across user locales. Without
     * Locale.US, a German device formats `52.52` as `52,52` and an English
     * device formats it as `52.52` — same coordinates, different keys, no
     * cache hits ever.
     *
     * Without rounding at all, GPS jitter (e.g. 52.51972 → 52.51975 between
     * worker runs) would invalidate the cache on every call and burn the
     * 1M-calls/month free quota for no informational gain — weather
     * doesn't materially change within a few hundred metres.
     */
    private fun cacheKey(lat: Double, lon: Double): String =
        java.lang.String.format(java.util.Locale.US, "%.2f,%.2f", lat, lon)

    private fun cacheForecast(forecast: ForecastResponse, lat: Double, lon: Double) {
        try {
            sharedPreferences.edit().apply {
                putString(PREFS_FORECAST_KEY, gson.toJson(forecast))
                putLong(PREFS_FORECAST_TIMESTAMP_KEY, System.currentTimeMillis())
                putString(PREFS_FORECAST_LOCATION_KEY, cacheKey(lat, lon))
                apply()
            }
        } catch (e: Exception) {
            com.example.plantcare.CrashReporter.log(e)
        }
    }

    /**
     * Aggregate the next 72 hours of forecast slots into a single watering
     * decision. Strictly more accurate than [getWateringAdvice] which only
     * looks at the current snapshot:
     *
     *  - **Heavy rain expected** (≥ 10 mm total over 72h or ≥ 70% pop on any
     *    slot) → factor 0.5, postpone aggressively.
     *  - **Some rain expected** (≥ 3 mm total or ≥ 40% pop average) → 0.7.
     *  - **Heat wave** (max temp ≥ 30 °C in any 3h slot AND avg humidity
     *    < 60%) → 1.5, pull reminders forward.
     *  - **Mild heat** (avg temp ≥ 25 °C) → 1.2.
     *  - **Cold snap** (avg temp < 5 °C) → 0.5.
     *  - **High humidity** (avg ≥ 80%) → 0.7.
     *  - Otherwise normal (1.0).
     *
     * Returns null if the forecast list is empty/null — caller should fall
     * back to current-weather advice.
     */
    fun getForecastBasedAdvice(forecast: ForecastResponse): WateringAdvice? {
        val slots = forecast.list?.takeIf { it.isNotEmpty() } ?: return null

        // Limit to the next ~72h (24 slots × 3h). Forecast accuracy degrades
        // beyond 3 days, and reminder shifts are capped at 7 days anyway.
        val nowEpoch = System.currentTimeMillis() / 1000L
        val cutoffEpoch = nowEpoch + 72 * 3600
        val window = slots.filter { it.dt in nowEpoch..cutoffEpoch }
        if (window.isEmpty()) return null

        val totalRainMm = window.sumOf { it.rain?.threeHour ?: 0.0 }
        val maxPop = window.maxOfOrNull { it.pop ?: 0.0 } ?: 0.0
        val avgPop = window.mapNotNull { it.pop }.takeIf { it.isNotEmpty() }?.average() ?: 0.0
        val temps = window.mapNotNull { it.main?.temp }
        val avgTemp = temps.takeIf { it.isNotEmpty() }?.average() ?: 20.0
        val maxTemp = temps.maxOrNull() ?: avgTemp
        val avgHumidity = window.mapNotNull { it.main?.humidity }
            .takeIf { it.isNotEmpty() }?.average() ?: 50.0

        return when {
            totalRainMm >= 10.0 || maxPop >= 0.7 -> WateringAdvice(
                adjustmentFactor = 0.5f,
                tipMessage = "Starker Regen in den nächsten 3 Tagen erwartet — gieße deutlich seltener.",
                iconEmoji = "🌧️"
            )
            totalRainMm >= 3.0 || avgPop >= 0.4 -> WateringAdvice(
                adjustmentFactor = 0.7f,
                tipMessage = "Etwas Regen in der Vorhersage — du kannst das Gießen verschieben.",
                iconEmoji = "🌦️"
            )
            maxTemp >= 30.0 && avgHumidity < 60.0 -> WateringAdvice(
                adjustmentFactor = 1.5f,
                tipMessage = "Hitzewelle in Sicht! Pflanzen früher gießen.",
                iconEmoji = "☀️"
            )
            avgTemp >= 25.0 -> WateringAdvice(
                adjustmentFactor = 1.2f,
                tipMessage = "Warme Tage erwartet — etwas häufiger gießen.",
                iconEmoji = "🌤️"
            )
            avgTemp < 5.0 -> WateringAdvice(
                adjustmentFactor = 0.5f,
                tipMessage = "Kälteeinbruch erwartet — Gießen pausieren.",
                iconEmoji = "❄️"
            )
            avgHumidity >= 80.0 -> WateringAdvice(
                adjustmentFactor = 0.7f,
                tipMessage = "Hohe Luftfeuchtigkeit in den nächsten Tagen — weniger gießen.",
                iconEmoji = "💨"
            )
            else -> WateringAdvice(
                adjustmentFactor = 1.0f,
                tipMessage = "Wetter bleibt stabil — gieße wie gewohnt.",
                iconEmoji = "🌤️"
            )
        }
    }

    /**
     * Get watering advice based on weather conditions.
     * Returns adjustment factor for watering frequency and localized German tip message.
     *
     * @param weather WeatherResponse containing weather data
     * @return WateringAdvice with adjustment factor and tip message
     */
    fun getWateringAdvice(weather: WeatherResponse): WateringAdvice {
        val temp = weather.main?.temp ?: 20.0
        val humidity = weather.main?.humidity ?: 50
        val isRaining = weather.rain?.oneHour?.let { it > 0.0 } ?: false

        return when {
            isRaining -> WateringAdvice(
                adjustmentFactor = 0.5f,
                tipMessage = "Es regnet! Deine Pflanzen brauchen heute weniger Wasser.",
                iconEmoji = "🌧️"
            )
            temp > 30 -> WateringAdvice(
                adjustmentFactor = 1.5f,
                tipMessage = "Hitze! Deine Pflanzen brauchen mehr Wasser als gewöhnlich.",
                iconEmoji = "☀️"
            )
            humidity > 80 -> WateringAdvice(
                adjustmentFactor = 0.7f,
                tipMessage = "Hohe Luftfeuchtigkeit – weniger gießen.",
                iconEmoji = "💨"
            )
            temp < 5 -> WateringAdvice(
                adjustmentFactor = 0.5f,
                tipMessage = "Kalt draußen! Pflanzen brauchen jetzt weniger Wasser.",
                iconEmoji = "❄️"
            )
            else -> WateringAdvice(
                adjustmentFactor = 1.0f,
                tipMessage = "Normales Wetter – gieße wie gewohnt.",
                iconEmoji = "🌤️"
            )
        }
    }

    companion object {
        private val OPENWEATHERMAP_API_KEY = BuildConfig.OPENWEATHER_API_KEY
        private const val CACHE_DURATION_MS = 3 * 60 * 60 * 1000 // 3 hours in milliseconds
        private const val FORECAST_CACHE_DURATION_MS = 12 * 60 * 60 * 1000L // 12h: forecast updates slowly
        private const val PREFS_NAME = "weather_cache"
        private const val PREFS_WEATHER_KEY = "cached_weather"
        private const val PREFS_TIMESTAMP_KEY = "cached_weather_timestamp"
        private const val PREFS_LOCATION_KEY = "cached_weather_location"
        private const val PREFS_FORECAST_KEY = "cached_forecast"
        private const val PREFS_FORECAST_TIMESTAMP_KEY = "cached_forecast_timestamp"
        private const val PREFS_FORECAST_LOCATION_KEY = "cached_forecast_location"

        @Volatile
        private var INSTANCE: WeatherRepository? = null

        @JvmStatic
        fun getInstance(context: Context): WeatherRepository {
            // #5 fix: inner recheck.
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WeatherRepository(context.applicationContext)
                    .also { INSTANCE = it }
            }
        }
    }
}

/**
 * Data class representing watering advice based on current weather conditions.
 *
 * @param adjustmentFactor Factor to adjust watering frequency (0.5 = half, 1.0 = normal, 1.5 = increase)
 * @param tipMessage Localized German tip message about current weather
 * @param iconEmoji Weather emoji to visually represent conditions
 */
data class WateringAdvice(
    val adjustmentFactor: Float,
    val tipMessage: String,
    val iconEmoji: String
)
