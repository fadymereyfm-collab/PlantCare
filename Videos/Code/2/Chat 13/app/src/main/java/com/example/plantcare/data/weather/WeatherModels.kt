package com.example.plantcare.data.weather

import com.google.gson.annotations.SerializedName

/**
 * Response data class for OpenWeatherMap Current Weather API.
 * Contains weather information for a specific location.
 */
data class WeatherResponse(
    @SerializedName("coord")
    val coord: Coord?,
    @SerializedName("weather")
    val weather: List<WeatherCondition>?,
    @SerializedName("main")
    val main: WeatherMain?,
    @SerializedName("wind")
    val wind: WeatherWind?,
    @SerializedName("rain")
    val rain: WeatherRain?,
    @SerializedName("name")
    val name: String?
)

/**
 * Coordinates (latitude and longitude) for the location.
 */
data class Coord(
    @SerializedName("lon")
    val lon: Double?,
    @SerializedName("lat")
    val lat: Double?
)

/**
 * Main weather data including temperature and humidity.
 */
data class WeatherMain(
    @SerializedName("temp")
    val temp: Double?,
    @SerializedName("humidity")
    val humidity: Int?
)

/**
 * Wind data including speed.
 */
data class WeatherWind(
    @SerializedName("speed")
    val speed: Double?
)

/**
 * Precipitation data.
 */
data class WeatherRain(
    @SerializedName("1h")
    val oneHour: Double?
)

/**
 * Weather condition description.
 */
data class WeatherCondition(
    @SerializedName("id")
    val id: Int?,
    @SerializedName("main")
    val main: String?,
    @SerializedName("description")
    val description: String?
)

/**
 * Response data class for OpenWeatherMap 5-day / 3-hour forecast endpoint
 * (`/data/2.5/forecast`). Returns up to 40 [ForecastSlot] entries — one per
 * 3-hour window over the next ~120 hours.
 *
 * Used by `WeatherRepository.getForecastBasedAdvice` (Functional Report §2.3,
 * task F7) to decide watering shifts based on aggregated 72-hour conditions
 * rather than a single "right now" snapshot.
 */
data class ForecastResponse(
    @SerializedName("list")
    val list: List<ForecastSlot>?,
    @SerializedName("city")
    val city: ForecastCity?
)

data class ForecastCity(
    @SerializedName("name")
    val name: String?,
    @SerializedName("country")
    val country: String?
)

/**
 * Single 3-hour forecast window. [dt] is epoch seconds (UTC) for the start
 * of the window; OpenWeather always returns slots aligned to 00:00, 03:00,
 * 06:00, … so we don't have to round.
 */
data class ForecastSlot(
    @SerializedName("dt")
    val dt: Long,
    @SerializedName("main")
    val main: WeatherMain?,
    @SerializedName("weather")
    val weather: List<WeatherCondition>?,
    @SerializedName("rain")
    val rain: ForecastRain?,
    @SerializedName("pop")
    val pop: Double?
)

/**
 * Forecast precipitation block. The forecast endpoint reports `3h` (rain over
 * the 3-hour window in mm) — a different key than the current-weather endpoint
 * which uses `1h`. Kept as a separate type so callers don't get confused.
 */
data class ForecastRain(
    @SerializedName("3h")
    val threeHour: Double?
)
