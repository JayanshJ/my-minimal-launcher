package com.minimal.launcher

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.*

/**
 * Calculates sunrise and sunset times using the NOAA simplified solar algorithm.
 * Accurate to within ~1 minute for latitudes between ±66°.
 */
object SunCalculator {

    /**
     * Returns (sunriseEpochMs, sunsetEpochMs) for today at [lat]/[lng], or null for polar day/night.
     */
    fun today(lat: Double, lng: Double): Pair<Long, Long>? {
        val dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)

        // Fractional year in radians
        val g = 2.0 * PI / 365.0 * (dayOfYear - 1)

        // Equation of time (minutes)
        val eqTime = 229.18 * (
                0.000075
                + 0.001868 * cos(g) - 0.032077 * sin(g)
                - 0.014615 * cos(2 * g) - 0.04089  * sin(2 * g))

        // Solar declination (radians)
        val decl = (0.006918
                - 0.399912 * cos(g) + 0.070257 * sin(g)
                - 0.006758 * cos(2 * g) + 0.000907 * sin(2 * g)
                - 0.002697 * cos(3 * g) + 0.00148  * sin(3 * g))

        // Hour angle — 90.833° accounts for atmospheric refraction + solar disc radius
        val latR  = lat * PI / 180.0
        val cosHa = (cos(90.833 * PI / 180.0) - sin(latR) * sin(decl)) /
                    (cos(latR) * cos(decl))
        if (cosHa < -1.0 || cosHa > 1.0) return null   // polar day or night
        val ha = acos(cosHa) * 180.0 / PI               // degrees

        // Solar noon + rise/set in minutes past midnight UTC
        val noonUtc = 720.0 - 4.0 * lng - eqTime
        val riseUtc = noonUtc - 4.0 * ha
        val setUtc  = noonUtc + 4.0 * ha

        // Epoch ms at midnight UTC
        val midnightUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        return Pair(
            midnightUtc + (riseUtc * 60_000L).toLong(),
            midnightUtc + (setUtc  * 60_000L).toLong()
        )
    }
}
