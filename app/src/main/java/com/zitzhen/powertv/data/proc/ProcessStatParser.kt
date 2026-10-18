package com.zitzhen.powertv.data.proc

/**
 * Parses `/proc/<pid>/stat`.
 *
 * The process name is wrapped in parentheses and may itself contain spaces or
 * parentheses, so the parser starts from the last closing parenthesis.
 */
object ProcessStatParser {

    data class ProcessStat(
        val pid: Int,
        val command: String,
        val state: String,
        val parentPid: Int,
        val cpuTicks: Long,
        val residentPages: Long,
        val startTimeTicks: Long,
    )

    fun parse(text: String): ProcessStat? {
        val open = text.indexOf('(')
        val close = text.lastIndexOf(')')
        if (open <= 0 || close <= open) return null

        val pid = text.substring(0, open).trim().toIntOrNull() ?: return null
        val command = text.substring(open + 1, close)
        val fields = text.substring(close + 1).trim().split(Regex("\\s+"))
        if (fields.size < 22) return null

        val state = fields[0]
        val parentPid = fields[1].toIntOrNull() ?: return null
        val userTicks = fields[11].toLongOrNull() ?: return null
        val systemTicks = fields[12].toLongOrNull() ?: return null
        val startTime = fields[19].toLongOrNull() ?: return null
        val residentPages = fields[21].toLongOrNull() ?: return null

        return ProcessStat(
            pid = pid,
            command = command,
            state = state,
            parentPid = parentPid,
            cpuTicks = userTicks + systemTicks,
            residentPages = residentPages,
            startTimeTicks = startTime,
        )
    }
}
