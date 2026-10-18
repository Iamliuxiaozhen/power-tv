package com.zitzhen.powertv.data.proc

import android.app.ActivityManager
import android.content.Context
import com.zitzhen.powertv.data.model.MemoryMetrics
import java.io.File

class MemorySampler(
    context: Context,
    private val memInfoFile: File = File("/proc/meminfo"),
) {
    private val activityManager = context.getSystemService(ActivityManager::class.java)

    fun sample(): MemoryMetrics {
        val memInfo = runCatching { MemInfoParser.parse(memInfoFile.readText()) }
            .getOrDefault(emptyMap())

        val fallback = ActivityManager.MemoryInfo()
        runCatching { activityManager?.getMemoryInfo(fallback) }

        val total = memInfo["MemTotal"] ?: fallback.totalMem
        val available = (memInfo["MemAvailable"] ?: fallback.availMem).coerceIn(0L, total)
        val cached = (memInfo["Cached"] ?: 0L) + (memInfo["Buffers"] ?: 0L)
        val swapTotal = memInfo["SwapTotal"] ?: 0L
        val swapFree = (memInfo["SwapFree"] ?: swapTotal).coerceIn(0L, swapTotal)

        return MemoryMetrics(
            totalBytes = total,
            availableBytes = available,
            usedBytes = (total - available).coerceAtLeast(0L),
            cachedBytes = cached.coerceAtMost(total),
            swapTotalBytes = swapTotal,
            swapFreeBytes = swapFree,
        )
    }
}
