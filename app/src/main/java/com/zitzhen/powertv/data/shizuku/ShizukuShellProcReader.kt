package com.zitzhen.powertv.data.shizuku

import android.util.Log
import com.zitzhen.powertv.data.proc.ProcRecord
import com.zitzhen.powertv.data.proc.ProcessStatParser
import com.zitzhen.powertv.data.proc.ProcessTableCodec
import rikka.shizuku.Shizuku
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Reads `/proc` through a shell process spawned by the Shizuku server itself
 * (`Shizuku.newProcess`). The shell is forked by the already running Shizuku
 * daemon, not by the app framework, so this path does not start a user service
 * and is unaffected by ROMs where user services crash during startup (e.g. the
 * MediaTek/Xiaomi `LoadedApk.makeApplicationInner` bug, Shizuku issue #1198).
 *
 * Unlike the ActivityManager binder API, a real shell can read every
 * `/proc/<pid>` entry, so per-process CPU ticks and RSS stay available. The
 * only cost is one short-lived shell process per read.
 *
 * Process names come from `/proc/<pid>/status`, which is the kernel `comm`
 * field and therefore capped at 15 characters.
 */
object ShizukuShellProcReader {

    private const val TAG = "ShizukuShellProcReader"
    private const val SHELL = "/system/bin/sh"
    private const val TABLE_MARKER = "@@STATUS"
    private const val PROC_PREFIX = "/proc/"
    private const val STATUS_SUFFIX = "/status"
    private const val MAX_OUTPUT_BYTES = 4 * 1024 * 1024

    /**
     * Concatenating every `stat` file gives one line per process, and `grep -H`
     * prefixes each metadata line with its own path, which keeps the pid
     * association intact even when some files cannot be read.
     */
    private val PROCESS_SCRIPT = """
        cat /proc/[0-9]*/stat
        echo $TABLE_MARKER
        grep -H -E '^(Name|Uid):' /proc/[0-9]*/status
    """.trimIndent()

    /** Raw `/proc/stat` contents used for total CPU accounting. */
    fun readCpuStat(): String? = runCatching {
        runShell("cat /proc/stat")
    }.onFailure { Log.w(TAG, "Shell /proc/stat read failed", it) }.getOrNull()

    /** Encoded process table, or null when the shell read fails. */
    fun readProcessTable(): String? = runCatching {
        buildProcessTable()
    }.onFailure { Log.w(TAG, "Shell process table read failed", it) }.getOrNull()

    private fun buildProcessTable(): String? {
        val output = runShell(PROCESS_SCRIPT)
        if (output == null) {
            Log.w(TAG, "Shell returned no output for the process table")
            return null
        }
        val parts = output.split("\n$TABLE_MARKER\n", limit = 2)
        if (parts.size != 2) {
            Log.w(TAG, "Shell output is missing the $TABLE_MARKER marker")
            return null
        }

        val nameByPid = HashMap<Int, String>()
        val uidByPid = HashMap<Int, Int>()
        parts[1].lineSequence().forEach { line ->
            val separator = line.indexOf(':')
            if (separator <= 0) return@forEach
            val path = line.substring(0, separator)
            if (!path.startsWith(PROC_PREFIX) || !path.endsWith(STATUS_SUFFIX)) {
                return@forEach
            }
            val pid = path.substring(PROC_PREFIX.length, path.length - STATUS_SUFFIX.length)
                .toIntOrNull() ?: return@forEach
            val content = line.substring(separator + 1)
            when {
                content.startsWith("Name:") ->
                    nameByPid[pid] = content.substring(5).trim()

                content.startsWith("Uid:") -> uidByPid[pid] = content.substring(4)
                    .trim()
                    .split(Regex("\\s+"))
                    .firstOrNull()
                    ?.toIntOrNull() ?: return@forEach
            }
        }

        val records = parts[0].lineSequence().mapNotNull { line ->
            val stat = ProcessStatParser.parse(line) ?: return@mapNotNull null
            val uid = uidByPid[stat.pid]
            // Kernel threads and zombies own no memory; they only add noise to
            // a task list.
            if (uid == 0 && stat.residentPages <= 0L) return@mapNotNull null
            ProcRecord(
                pid = stat.pid,
                uid = uid,
                state = stat.state,
                residentPages = stat.residentPages,
                cpuTicks = stat.cpuTicks,
                startTimeTicks = stat.startTimeTicks,
                name = nameByPid[stat.pid] ?: stat.command,
            )
        }.toList()

        if (records.isEmpty()) {
            Log.w(TAG, "Shell parsed no process records")
            return null
        }
        return ProcessTableCodec.encode(records)
    }

    /**
     * Runs [script] in a shell created by the Shizuku server and returns its
     * stdout. The script is fixed and local, so it cannot hang on user input.
     *
     * `Shizuku.newProcess` is the library's own process-spawning entry point,
     * but since API 13 the method is private because the library wants apps to
     * use user services instead. User services are exactly what crashes on the
     * affected ROMs, so the deprecated method is reached reflectively; see the
     * keep rule in proguard-rules.pro.
     */
    private fun runShell(script: String): String? {
        val method = newProcessMethod ?: return null
        val process = try {
            method.invoke(
                null,
                arrayOf(SHELL, "-c", "exec 2>/dev/null\n$script"),
                null,
                null,
            ) as? Process ?: return null
        } catch (error: Throwable) {
            Log.w(TAG, "Cannot spawn a Shizuku shell", error)
            return null
        }
        return try {
            val output = process.inputStream.use(::readCapped)
            process.waitFor()
            output
        } finally {
            runCatching { process.destroy() }
        }
    }

    private val newProcessMethod: java.lang.reflect.Method? by lazy {
        runCatching {
            Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            ).apply { isAccessible = true }
        }.onFailure {
            Log.w(TAG, "Shizuku.newProcess is unavailable; update Shizuku", it)
        }.getOrNull()
    }

    private fun readCapped(stream: InputStream): String? {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(64 * 1024)
        var total = 0
        while (true) {
            val read = stream.read(chunk)
            if (read < 0) break
            total += read
            if (total > MAX_OUTPUT_BYTES) return null
            buffer.write(chunk, 0, read)
        }
        return buffer.toString(Charsets.UTF_8.name())
    }
}
