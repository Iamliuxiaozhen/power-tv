package com.zitzhen.powertv.data.proc

import android.content.Context
import android.system.Os
import android.system.OsConstants
import com.zitzhen.powertv.data.model.ProcessInfo
import com.zitzhen.powertv.data.model.ProcessSnapshot
import java.io.File

/**
 * Best-effort process scanner.
 *
 * Android hides other UIDs' `/proc` entries from normal apps on modern
 * releases. The scanner lists everything readable, clearly reports reduced
 * visibility, and never requires root.
 */
class ProcessSampler(
    context: Context,
    private val procRoot: File = File("/proc"),
) {
    private data class RawProcess(
        val info: ProcessInfo,
        val cpuTicks: Long,
        val startTimeTicks: Long,
    )

    private data class Ticks(val cpuTicks: Long, val startTimeTicks: Long)

    private val ownUid = context.applicationInfo.uid
    private val pageSize = runCatching {
        Os.sysconf(OsConstants._SC_PAGESIZE)
    }.getOrDefault(4096L)
    private val coreCount = Runtime.getRuntime().availableProcessors()

    private var previousTicks: Map<Int, Ticks> = emptyMap()
    private var previousTotalTicks: Long? = null

    fun sample(): ProcessSnapshot {
        val currentTotalTicks = readTotalCpuTicks()
        val totalDelta = previousTotalTicks?.let { previous ->
            currentTotalTicks?.minus(previous)?.takeIf { it > 0L }
        }

        val rawProcesses = procRoot.listFiles()
            ?.asSequence()
            ?.filter { it.isDirectory && it.name.all(Char::isDigit) }
            ?.mapNotNull { dir -> readProcess(dir) }
            ?.toList()
            ?: emptyList()

        val processes = rawProcesses
            .map { raw ->
                val previous = previousTicks[raw.info.pid]
                val cpu = if (
                    previous != null &&
                    previous.startTimeTicks == raw.startTimeTicks &&
                    totalDelta != null
                ) {
                    val processDelta = raw.cpuTicks - previous.cpuTicks
                    if (processDelta >= 0L) {
                        (processDelta.toDouble() / totalDelta * coreCount * 100.0)
                            .toFloat()
                            .coerceIn(0f, coreCount * 100f)
                    } else {
                        null
                    }
                } else {
                    null
                }
                raw.info.copy(cpuPercent = cpu)
            }
            .sortedWith(compareByDescending<ProcessInfo> { it.cpuPercent ?: 0f }
                .thenByDescending { it.memoryBytes })
            .take(MAX_PROCESSES)

        previousTicks = rawProcesses.associate { raw ->
            raw.info.pid to Ticks(raw.cpuTicks, raw.startTimeTicks)
        }
        previousTotalTicks = currentTotalTicks

        return ProcessSnapshot(
            processes = processes,
            limitedVisibility = processes.none { it.uid != null && it.uid != ownUid },
            sampledAtMillis = System.currentTimeMillis(),
        )
    }

    private fun readProcess(dir: File): RawProcess? {
        val statText = runCatching { File(dir, "stat").readText() }.getOrNull() ?: return null
        val stat = ProcessStatParser.parse(statText) ?: return null

        val cmdline = runCatching {
            File(dir, "cmdline").readBytes()
                .toString(Charsets.UTF_8)
                .substringBefore('\u0000')
                .trim()
        }.getOrNull()
        // Kernel threads have no command line and are not useful in a task
        // manager; focusing on user-space processes also keeps polling cheap.
        val commandLine = cmdline?.takeIf { it.isNotBlank() } ?: return null

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

        val info = ProcessInfo(
            pid = stat.pid,
            name = commandLine.substringAfterLast('/').ifBlank { stat.command }.take(80),
            state = stat.state,
            uid = uid,
            memoryBytes = (stat.residentPages * pageSize).coerceAtLeast(0L),
            cpuPercent = null,
            isOwnProcess = uid == ownUid,
        )
        return RawProcess(info = info, cpuTicks = stat.cpuTicks, startTimeTicks = stat.startTimeTicks)
    }

    private fun readTotalCpuTicks(): Long? = runCatching {
        CpuStatParser.parse(File(procRoot, "stat").readText())["cpu"]?.total
    }.getOrNull()

    private companion object {
        const val MAX_PROCESSES = 80
    }
}
