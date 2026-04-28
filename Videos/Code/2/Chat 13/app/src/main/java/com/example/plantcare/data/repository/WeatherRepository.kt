package com.example.plantcare.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.plantcare.BuildConfig
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
class WeatherRepository private constructor(private val context: Context) {

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
                e.printStackTrace()
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
        val expectedLocation = "$lat,$lon"
        if (cachedLocation != expectedLocation) {
            return null
        }

        return try {
            gson.fromJson(cachedJson, WeatherResponse::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
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
                putString(PREFS_LOCATION_KEY, "$lat,$lon")
                apply()
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
        private const val PREFS_NAME = "weather_cache"
        private const val PREFS_WEATHER_KEY = "cached_weather"
        private const val PREFS_TIMESTAMP_KEY = "cached_weather_timestamp"
        private const val PREFS_LOCATION_KEY = "cached_weather_location"

        @Volatile
        private var INSTANCE: WeatherRepository? = null

        fun getInstance(context: Context): WeatherRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = WeatherRepository(context)
                INSTANCE = instance
                instance
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
