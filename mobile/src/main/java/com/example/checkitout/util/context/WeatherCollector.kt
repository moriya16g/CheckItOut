package com.example.checkitout.util.context

import com.example.checkitout.util.Http
import org.json.JSONObject

/**
 * Open-Meteo current conditions. Free, no API key required.
 * https://open-meteo.com/
 */
object WeatherCollector {

    data class Weather(
        val code: String,        // human-readable category
        val tempC: Float?,
        val humidityPct: Float?,
    )

    suspend fun collect(lat: Double, lng: Double): Weather? {
        val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$lat&longitude=$lng" +
                "&current=temperature_2m,relative_humidity_2m,weather_code"
        val body = Http.get(url) ?: return null
        return try {
            val current = JSONObject(body).optJSONObject("current") ?: return null
            val code = wmoCodeToLabel(current.optInt("weather_code", -1))
            val temp = current.opt("temperature_2m") as? Number
            val hum = current.opt("relative_humidity_2m") as? Number
            Weather(code, temp?.toFloat(), hum?.toFloat())
        } catch (_: Throwable) {
            null
        }
    }

    /** WMO weather interpretation codes → coarse label. */
    private fun wmoCodeToLabel(code: Int): String = when (code) {
        0 -> "clear"
        1, 2 -> "mostly_clear"
        3 -> "cloudy"
        45, 48 -> "fog"
        51, 53, 55, 56, 57 -> "drizzle"
        61, 63, 65, 66, 67, 80, 81, 82 -> "rain"
        71, 73, 75, 77, 85, 86 -> "snow"
        95, 96, 99 -> "thunder"
        else -> "unknown"
    }
}
