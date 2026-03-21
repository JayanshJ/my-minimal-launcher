package com.minimal.launcher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

/**
 * Fetches current weather from Open-Meteo (no API key required).
 * Returns a single plain-text string suitable for the widget panel.
 */
object WeatherManager {

    data class WeatherData(val display: String, val code: Int = -1)

    /**
     * Performs a blocking network call — must be called from a coroutine
     * on Dispatchers.IO (handled internally via [withContext]).
     */
    suspend fun fetch(lat: Double, lon: Double, useFahrenheit: Boolean): WeatherData? =
        withContext(Dispatchers.IO) {
            try {
                val unit = if (useFahrenheit) "fahrenheit" else "celsius"
                val raw  = URL(
                    "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=$lat&longitude=$lon" +
                    "&current_weather=true" +
                    "&temperature_unit=$unit"
                ).readText()

                val current = JSONObject(raw).getJSONObject("current_weather")
                val temp    = current.getDouble("temperature")
                val code    = current.getInt("weathercode")
                val suffix  = if (useFahrenheit) "°F" else "°C"
                val label   = wmoToText(code)

                val display = buildString {
                    append("%.0f%s".format(temp, suffix))
                    if (label.isNotEmpty()) append("  $label")
                }
                WeatherData(display, code)
            } catch (_: Exception) {
                null
            }
        }

    /** Maps WMO weather interpretation codes to short plain-text descriptions. */
    private fun wmoToText(code: Int): String = when (code) {
        0            -> "clear"
        1            -> "mostly clear"
        2            -> "partly cloudy"
        3            -> "overcast"
        45, 48       -> "foggy"
        51, 53, 55   -> "drizzle"
        56, 57       -> "freezing drizzle"
        61, 63, 65   -> "rain"
        66, 67       -> "freezing rain"
        71, 73, 75   -> "snow"
        77           -> "snow grains"
        80, 81, 82   -> "showers"
        85, 86       -> "snow showers"
        95           -> "thunderstorm"
        96, 99       -> "hail"
        else         -> ""
    }
}
