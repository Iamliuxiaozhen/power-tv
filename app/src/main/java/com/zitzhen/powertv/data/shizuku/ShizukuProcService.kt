package com.zitzhen.powertv.data.shizuku

import com.zitzhen.powertv.data.proc.ProcRecord
import com.zitzhen.powertv.data.proc.ProcessStatParser
import com.zitzhen.powertv.data.proc.ProcessTableCodec
import java.io.File

/**
 * Runs inside the Shizuku user service process with ADB shell identity.
 *
 * Only file access lives here; parsing and sampling stay in the app process so
 * the service stays small and the pure logic remains unit-testable.
 */
class ShizukuProcService : IProcReader.Stub() {

    private val procRoot = File("/proc")

    override fun readCpuStat(): String = runCatching {
        File(procRoot, "stat").readText()
    }.getOrDefault("")

    override fun readProcessTable(): String = runCatching {
        ProcessTableCodec.encode(readProcesses())
    }.getOrDefault("")

    override fun destroy() {
        System.exit(0)
    }

    private fun readProcesses(): List<ProcRecord> = procRoot.listFiles()
        ?.asSequence()
        ?.filter { it.isDirectory && it.name.all(Char::isDigit) }
        ?.mapNotNull(::readProcess)
        ?.toList()
        ?: emptyList()

    private fun readProcess(dir: File): ProcRecord? {
        val statText = runCatching { File(dir, "stat").readText() }.getOrNull() ?: return null
        val stat = ProcessStatParser.parse(statText) ?: return null

        val commandLine = runCatching {
            File(dir, "cmdline").readBytes()
                .toString(Charsets.UTF_8)
                .substringBefore('\u0000')
                .trim()
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null

        val uid = runCatching {
            File(dir, "status").useLines { lines ->
                lines.firstOrNull { it.startsWith("Uid:") }
                    ?.substringAfter(':')
                    ?.trim()
                    ?.split(Regex("\\s+"))
                    ?.firstOrNull()
                    ?.toIntOrNull()
            }
        }.getOrNull()

        return ProcRecord(
            pid = stat.pid,
            uid = uid,
            state = stat.state,
            residentPages = stat.residentPages,
            cpuTicks = stat.cpuTicks,
            startTimeTicks = stat.startTimeTicks,
            name = commandLine.substringAfterLast('/').ifBlank { stat.command },
        )
    }
}
