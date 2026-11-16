package com.selawi.cockpit.features.vehicle

import java.util.Locale
import kotlin.math.roundToLong

/**
 * Short forms for the stats under the car or plane, so every value fits its column at the same
 * size: at most six characters, however long the trip or flight.
 */
object StatFormat {
    /** 9,999 in full; then 12.8K, 128K, 1.3M, 12M. A ".0" is dropped (12K, not 12.0K). */
    fun number(n: Long): String = when {
        n < 0 -> "-" + number(-n)
        n < 10_000 -> String.format(Locale.US, "%,d", n)
        n < 999_950 -> scaled(n / 1_000.0, "K")
        else -> scaled(n / 1_000_000.0, "M")
    }

    /** A distance in km: one decimal below 100 (23.4), whole above, then as [number]. */
    fun km(km: Double): String = if (km < 99.95) String.format(Locale.US, "%.1f", km) else number(km.roundToLong())

    /** 45s, 52m, 9h 52m; from ten hours 13.9h, from a hundred 128h. */
    fun duration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        return when {
            seconds < 60 -> "${seconds}s"
            h == 0L -> "${m}m"
            h < 10 -> "${h}h ${m}m"
            seconds < 359_820 -> trim(String.format(Locale.US, "%.1f", seconds / 3600.0)) + "h"
            else -> "${(seconds / 3600.0).roundToLong()}h"
        }
    }

    /** One decimal below 100 of the unit (12.8K), whole numbers above (128K). */
    private fun scaled(value: Double, unit: String): String =
        if (value < 99.95) trim(String.format(Locale.US, "%.1f", value)) + unit
        else "${value.roundToLong()}$unit"

    private fun trim(s: String) = if (s.endsWith(".0")) s.dropLast(2) else s
}
