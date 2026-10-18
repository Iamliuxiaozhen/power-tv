package com.zitzhen.powertv.data.proc

/**
 * Parses the CPU lines exposed by `/proc/stat`.
 *
 * The first line (`cpu`) is the aggregate of all cores; the following lines
 * (`cpu0`, `cpu1`, ...) describe individual cores.
 */
object CpuStatParser {

    data class CpuTimes(val total: Long, val idle: Long)

    fun parse(stat: String): Map<String, CpuTimes> {
        val result = linkedMapOf<String, CpuTimes>()
        stat.lineSequence()
            .filter { it.startsWith("cpu") }
            .forEach { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size < 5) return@forEach
                val name = parts[0]
                if (!CPU_LINE.matches(name)) return@forEach
                val values = parts.drop(1).mapNotNull { it.toLongOrNull() }
                if (values.size < 4) return@forEach
                // user, nice, system, idle, iowait, irq, softirq, steal
                val idle = values[3] + values.getOrElse(4) { 0L }
                val total = values.take(8).sum()
                if (total > 0) result[name] = CpuTimes(total = total, idle = idle)
            }
        return result
    }

    /**
     * Returns the busy percentage between two samples, or null when there is
     * no meaningful delta yet (first sample, counter reset, or core offline).
     */
    fun usage(previous: CpuTimes?, current: CpuTimes): Float? {
        if (previous == null) return null
        val totalDelta = current.total - previous.total
        val idleDelta = current.idle - previous.idle
        if (totalDelta <= 0L) return null
        val busy = (totalDelta - idleDelta).toDouble() / totalDelta
        return (busy * 100.0).toFloat().coerceIn(0f, 100f)
    }

    private val CPU_LINE = Regex("cpu\\d*")
}
