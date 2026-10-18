package com.zitzhen.powertv.data.proc

/** Parses `/proc/meminfo`, converting its kB values to bytes. */
object MemInfoParser {

    fun parse(text: String): Map<String, Long> {
        val result = linkedMapOf<String, Long>()
        text.lineSequence().forEach { line ->
            val separator = line.indexOf(':')
            if (separator <= 0) return@forEach
            val key = line.substring(0, separator).trim()
            val value = line.substring(separator + 1)
                .trim()
                .split(Regex("\\s+"))
                .firstOrNull()
                ?.toLongOrNull()
                ?: return@forEach
            result[key] = value * 1024L
        }
        return result
    }
}
