package com.zitzhen.powertv.data.proc

import android.os.Build
import com.zitzhen.powertv.data.model.CpuCoreMetrics
import com.zitzhen.powertv.data.model.CpuMetrics
import java.io.File

/**
 * Reads CPU utilisation and per-core frequencies without root.
 *
 * `/proc/stat` and most `cpufreq` nodes are readable on stock Android. When a
 * vendor blocks them the sampler degrades gracefully instead of failing the
 * whole refresh.
 */
class CpuSampler(
    private val procStatFile: File = File("/proc/stat"),
    private val procCpuInfoFile: File = File("/proc/cpuinfo"),
    private val sysCpuRoot: File = File("/sys/devices/system/cpu"),
) {
    private var previousTimes: Map<String, CpuStatParser.CpuTimes> = emptyMap()
    private val model: String? by lazy { readCpuModel() }

    fun sample(): CpuMetrics {
        val currentTimes = runCatching {
            CpuStatParser.parse(procStatFile.readText())
        }.getOrDefault(emptyMap())

        val usages = currentTimes.mapValues { (name, times) ->
            CpuStatParser.usage(previousTimes[name], times)
        }
        previousTimes = currentTimes

        val kernelCoreCount = currentTimes.keys.count { CORE_NAME.matches(it) }
        val sysfsCoreCount = countSysfsCores()
        val coreCount = maxOf(kernelCoreCount, sysfsCoreCount, Runtime.getRuntime().availableProcessors())

        val cores = (0 until coreCount).map { index ->
            val online = isOnline(index)
            val (currentFreq, maxFreq) = readFrequencies(index)
            CpuCoreMetrics(
                index = index,
                usagePercent = if (online) usages["cpu$index"] else null,
                currentFreqMhz = if (online) currentFreq else null,
                maxFreqMhz = maxFreq,
                online = online,
            )
        }

        return CpuMetrics(
            usagePercent = usages["cpu"],
            cores = cores,
            currentFreqMhz = cores.mapNotNull { it.currentFreqMhz }.maxOrNull(),
            maxFreqMhz = cores.mapNotNull { it.maxFreqMhz }.maxOrNull(),
            model = model,
            architecture = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
        )
    }

    private fun countSysfsCores(): Int =
        sysCpuRoot.listFiles()
            ?.count { CORE_NAME.matches(it.name) }
            ?: 0

    private fun isOnline(index: Int): Boolean {
        if (index == 0) return true
        val file = File(sysCpuRoot, "cpu$index/online")
        return file.readTextOrNull()?.trim() != "0"
    }

    private fun readFrequencies(index: Int): Pair<Int?, Int?> {
        val cpufreq = File(sysCpuRoot, "cpu$index/cpufreq")
        val currentKhz = cpufreq.resolve("scaling_cur_freq").readLongOrNull()
            ?: cpufreq.resolve("cpuinfo_cur_freq").readLongOrNull()
        val maxKhz = cpufreq.resolve("cpuinfo_max_freq").readLongOrNull()
            ?: cpufreq.resolve("scaling_max_freq").readLongOrNull()
        return currentKhz.toMhz() to maxKhz.toMhz()
    }

    private fun readCpuModel(): String? {
        val lines = runCatching { procCpuInfoFile.readLines() }.getOrDefault(emptyList())
        val keys = listOf("Hardware", "model name", "Processor", "CPU part")
        for (key in keys) {
            val value = lines.firstOrNull { it.startsWith("$key", ignoreCase = true) }
                ?.substringAfter(':')
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            if (value != null) return value
        }
        return null
    }

    private fun Long?.toMhz(): Int? = when {
        this == null || this <= 0L -> null
        this >= 10_000_000L -> (this / 1_000_000L).toInt()
        else -> (this / 1_000L).toInt()
    }

    private fun File.readTextOrNull(): String? =
        runCatching { if (canRead()) readText() else null }.getOrNull()

    private fun File.readLongOrNull(): Long? =
        readTextOrNull()?.trim()?.toLongOrNull()

    private companion object {
        val CORE_NAME = Regex("cpu\\d+")
    }
}
