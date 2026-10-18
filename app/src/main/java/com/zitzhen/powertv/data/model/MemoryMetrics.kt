package com.zitzhen.powertv.data.model

/** Physical memory and swap usage. All values are bytes. */
data class MemoryMetrics(
    val totalBytes: Long,
    val availableBytes: Long,
    val usedBytes: Long,
    val cachedBytes: Long,
    val swapTotalBytes: Long,
    val swapFreeBytes: Long,
) {
    val usedPercent: Float
        get() = if (totalBytes <= 0L) 0f else (usedBytes.toDouble() / totalBytes * 100).toFloat()

    val swapUsedBytes: Long
        get() = (swapTotalBytes - swapFreeBytes).coerceAtLeast(0L)
}
