package com.zitzhen.powertv.data.model

/** Live CPU information collected from the kernel and sysfs. */
data class CpuMetrics(
    val usagePercent: Float?,
    val cores: List<CpuCoreMetrics>,
    val currentFreqMhz: Int?,
    val maxFreqMhz: Int?,
    val model: String?,
    val architecture: String,
) {
    val onlineCores: Int
        get() = cores.count { it.online }
}

data class CpuCoreMetrics(
    val index: Int,
    val usagePercent: Float?,
    val currentFreqMhz: Int?,
    val maxFreqMhz: Int?,
    val online: Boolean,
)
