package com.zitzhen.powertv.data.system

/** Pure parsers for the vendor-specific GPU sysfs values. */
object GpuSysfsParser {

    /** Parses the Adreno `gpubusy` pair (`busy total`) into a percentage. */
    fun parseBusyPair(text: String): Float? {
        val values = NUMBERS.findAll(text).mapNotNull { it.value.toDoubleOrNull() }.toList()
        if (values.size < 2) return null
        val busy = values[0]
        val total = values[1]
        if (total <= 0.0) return null
        return (busy / total * 100.0).toFloat().coerceIn(0f, 100f)
    }

    /**
     * Parses a single utilization value. Mali often reports 0..100, some
     * kernels report a 0..1 fraction or append a percent sign.
     */
    fun parsePercentage(text: String): Float? {
        val value = NUMBERS.find(text)?.value?.toDoubleOrNull() ?: return null
        if (!value.isFinite() || value < 0.0) return null
        val percent = if (value <= 1.0 && !text.contains('%')) value * 100.0 else value
        return percent.toFloat().coerceIn(0f, 100f)
    }

    /** Converts hardware clock Hz/kHz values to MHz. */
    fun toMhz(value: Long?): Int? = when {
        value == null || value <= 0L -> null
        value >= 10_000_000L -> (value / 1_000_000L).toInt()
        value >= 10_000L -> (value / 1_000L).toInt()
        else -> value.toInt()
    }

    private val NUMBERS = Regex("-?\\d+(?:\\.\\d+)?")
}
