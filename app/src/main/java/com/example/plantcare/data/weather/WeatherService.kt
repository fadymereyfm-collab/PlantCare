package com.example.plantcare.data.weather

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import com.google.gson.GsonBuilder

/**
 * Retrofit service interface for OpenWeatherMap API.
 * Provides methods to fetch current weather data.
 */
interface WeatherService {

    /**
     * Fetch current weather for a specific location.
     *
     * @param lat Latitude of the location
     * @param lon Longitude of the location
     * @param apiKey OpenWeatherMap API key
     * @param units Unit system (metric = Celsius, imperial = Fahrenheit)
     * @param lang Language code (e.g., "de" for German)
     * @return WeatherResponse containing weather data
     */
    @GET("weather")
    suspend fun getCurrentWeather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric",
        @Query("lang") lang: String = "de"
    ): WeatherResponse

    /**
     * Fetch 5-day / 3-hour forecast for a specific location. Returns up to 40
     * [ForecastSlot] entries (one per 3-hour window). Same free-tier endpoint
     * as `getCurrentWeather`, no extra subscription required.
     *
     * Costs one API call per worker run — the 12-hour periodic schedule plus
     * a 12-hour cache means at most ~2 calls/day per device, well below the
     * 60 calls/min / 1M calls/month free quota.
     */
    @GET("forecast")
    suspend fun getForecast(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric",
        @Query("lang") lang: String = "de"
    ): ForecastResponse

    companion object {
        private const val BASE_URL = "https://api.openweathermap.org/data/2.5/"

        /**
         * Factory method to create a Retrofit instance for the OpenWeatherMap API.
         *
         * @return WeatherService instance
         */
        fun create(): WeatherService {
            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
                .build()

            return retrofit.create(WeatherService::class.java)
        }
    }
}
