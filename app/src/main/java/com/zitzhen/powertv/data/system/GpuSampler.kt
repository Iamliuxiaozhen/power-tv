package com.zitzhen.powertv.data.system

import com.zitzhen.powertv.data.model.GpuMetrics
import com.zitzhen.powertv.data.model.TemperatureKind
import com.zitzhen.powertv.data.model.ThermalMetrics
import java.io.File

/**
 * Reads common Adreno/Mali sysfs nodes. These paths are vendor specific and
 * frequently blocked on production devices, so every read is optional.
 */
class GpuSampler(
    private val sysRoot: File = File("/sys"),
) {
    private val kgsl = File(sysRoot, "class/kgsl/kgsl-3d0")
    private val mali = File(sysRoot, "class/misc/mali0/device")
    private val devfreqRoot = File(sysRoot, "class/devfreq")

    fun sample(thermal: ThermalMetrics? = null): GpuMetrics {
        val deviceName = when {
            kgsl.exists() -> "Adreno"
            mali.exists() -> "Mali"
            else -> "GPU"
        }
        val devfreq = findGpuDevfreq()

        val utilization = readFirst(
            File(kgsl, "gpubusy"),
            File(kgsl, "gpu_busy_percentage"),
            File(mali, "utilisation"),
            File(sysRoot, "kernel/gpu/gpu_busy"),
        ) { text ->
            if (text.trim().split(Regex("\\s+")).size >= 2) {
                GpuSysfsParser.parseBusyPair(text)
            } else {
                GpuSysfsParser.parsePercentage(text)
            }
        } ?: devfreq?.let { readUtilization(it) }

        val currentFreq = readFirstLong(
            File(kgsl, "gpuclk"),
            File(kgsl, "devfreq/cur_freq"),
            devfreq?.resolve("cur_freq"),
        ).let(GpuSysfsParser::toMhz)

        val maxFreq = readFirstLong(
            File(kgsl, "devfreq/max_freq"),
            devfreq?.resolve("max_freq"),
        ).let(GpuSysfsParser::toMhz)

        return GpuMetrics(
            name = deviceName,
            utilizationPercent = utilization,
            currentFreqMhz = currentFreq,
            maxFreqMhz = maxFreq,
            temperatureCelsius = thermal?.readingFor(TemperatureKind.GPU)?.celsius,
        )
    }

    private fun findGpuDevfreq(): File? {
        val entries = devfreqRoot.listFiles() ?: return null
        return entries.firstOrNull { entry ->
            val name = entry.name.lowercase()
            (name.contains("gpu") || name.contains("kgsl") || name.contains("mali")) &&
                File(entry, "cur_freq").canRead()
        }
    }

    private fun readUtilization(devfreq: File): Float? =
        readFirst(
            File(devfreq, "gpu_load"),
            File(devfreq, "utilisation"),
            File(devfreq, "load"),
        )(GpuSysfsParser::parsePercentage)

    private fun <T> readFirst(vararg files: File?, parser: (String) -> T?): T? {
        for (file in files) {
            if (file == null) continue
            val text = runCatching { if (file.canRead()) file.readText() else null }.getOrNull()
                ?: continue
            val parsed = runCatching { parser(text) }.getOrNull() ?: continue
            return parsed
        }
        return null
    }

    private fun readFirstLong(vararg files: File?): Long? =
        readFirst(*files) { it.trim().toLongOrNull() }
}
