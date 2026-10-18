package com.zitzhen.powertv.ui

import java.util.Locale
import kotlin.math.abs

/** Locale-stable display formatting for telemetry values. */
object Formatters {

    fun percent(value: Float?, decimals: Int = 0): String {
        if (value == null || !value.isFinite()) return "—"
        return String.format(Locale.US, "%.${decimals}f%%", value)
    }

    fun temperature(celsius: Float?): String {
        if (celsius == null || !celsius.isFinite()) return "—"
        return String.format(Locale.US, "%.1f°C", celsius)
    }

    fun frequency(mhz: Int?): String {
        if (mhz == null || mhz <= 0) return "—"
        return if (mhz >= 1_000) {
            String.format(Locale.US, "%.2f GHz", mhz / 1_000f)
        } else {
            "$mhz MHz"
        }
    }

    fun bytes(bytes: Long?): String {
        if (bytes == null || bytes < 0L) return "—"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024.0 && unit < units.lastIndex) {
            value /= 1024.0
            unit++
        }
        val decimals = when {
            unit <= 1 -> 0
            value >= 100 -> 0
            value >= 10 -> 1
            else -> 2
        }
        return String.format(Locale.US, "%.${decimals}f %s", value, units[unit])
    }

    fun current(milliamps: Int?): String {
        if (milliamps == null) return "—"
        val sign = if (milliamps < 0) "−" else ""
        return "$sign${abs(milliamps)} mA"
    }

    fun voltage(millivolts: Int?): String {
        if (millivolts == null || millivolts <= 0) return "—"
        return String.format(Locale.US, "%.2f V", millivolts / 1_000f)
    }

    fun uptime(millis: Long): String {
        val totalMinutes = millis.coerceAtLeast(0L) / 60_000L
        val days = totalMinutes / (24 * 60)
        val hours = totalMinutes % (24 * 60) / 60
        val minutes = totalMinutes % 60
        return when {
            days > 0 -> "${days}d ${hours}h"
            hours > 0 -> "${hours}h ${minutes}m"
            else -> "${minutes}m"
        }
    }

    fun timeOfDay(millis: Long): String =
        String.format(Locale.US, "%1\$tH:%1\$tM:%1\$tS", millis)
}
