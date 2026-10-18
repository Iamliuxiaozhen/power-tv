package com.zitzhen.powertv.data.model

/** Best-effort GPU telemetry. Android has no public GPU utilisation API. */
data class GpuMetrics(
    val name: String,
    val utilizationPercent: Float?,
    val currentFreqMhz: Int?,
    val maxFreqMhz: Int?,
    val temperatureCelsius: Float?,
) {
    val available: Boolean
        get() = utilizationPercent != null || currentFreqMhz != null || temperatureCelsius != null
}
