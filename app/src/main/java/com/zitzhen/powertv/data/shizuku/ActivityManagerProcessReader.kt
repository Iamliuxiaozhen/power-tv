package com.zitzhen.powertv.data.shizuku

import android.app.ActivityManager
import android.os.Debug
import android.os.IBinder
import android.system.Os
import android.system.OsConstants
import com.zitzhen.powertv.data.proc.ProcRecord
import com.zitzhen.powertv.data.proc.ProcessTableCodec
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * Reads the running process table by calling ActivityManager directly over a
 * Shizuku-wrapped binder. No user-service process is spawned, which keeps this
 * path working on ROMs where Shizuku user services crash at startup (e.g. the
 * MediaTek/Xiaomi `LoadedApk.makeApplicationInner` bug, Shizuku issue #1198).
 *
 * Trade-off: the binder API exposes no per-process CPU ticks, so cpuTicks and
 * startTimeTicks are encoded as [NO_CPU_DATA] and per-process CPU percentages
 * stay unavailable. Memory is PSS from `getProcessMemoryInfo` instead of RSS.
 *
 * All methods must be called from a background thread; failures return null so
 * callers can fall back to the unprivileged /proc scan.
 */
object ActivityManagerProcessReader {

    /** Sentinel written into cpuTicks/startTimeTicks: "no CPU data". */
    const val NO_CPU_DATA = -1L

    private const val STATE_UNKNOWN = "?"

    private val pageSize = runCatching {
        Os.sysconf(OsConstants._SC_PAGESIZE)
    }.getOrDefault(4096L)

    @Volatile
    private var proxy: Any? = null

    @Volatile
    private var runningAppProcessesMethod: java.lang.reflect.Method? = null

    @Volatile
    private var processMemoryInfoMethod: java.lang.reflect.Method? = null

    /** Returns the encoded process table, or null when the binder call fails. */
    fun readProcessTable(): String? = runCatching {
        ensureProxy()

        @Suppress("UNCHECKED_CAST")
        val running = runningAppProcessesMethod!!.invoke(proxy)
            as List<ActivityManager.RunningAppProcessInfo>

        val pssKbByPid = HashMap<Int, Int>(running.size)
        if (running.isNotEmpty()) {
            val pids = running.map { it.pid }.toIntArray()
            val memInfos = processMemoryInfoMethod!!.invoke(proxy, pids)
                as Array<Debug.MemoryInfo>
            pids.forEachIndexed { index, pid ->
                pssKbByPid[pid] = memInfos.getOrNull(index)?.totalPss ?: 0
            }
        }

        ProcessTableCodec.encode(running.map { info ->
            ProcRecord(
                pid = info.pid,
                uid = info.uid,
                state = STATE_UNKNOWN,
                residentPages = pssKbByPid[info.pid]
                    ?.toLong()
                    ?.times(1024L)
                    ?.div(pageSize)
                    ?: 0L,
                cpuTicks = NO_CPU_DATA,
                startTimeTicks = NO_CPU_DATA,
                name = info.processName,
            )
        })
    }.onFailure { resetProxy() }.getOrNull()

    private fun ensureProxy() {
        if (proxy != null) return
        synchronized(this) {
            if (proxy != null) return
            val binder = SystemServiceHelper.getSystemService("activity")
                ?: throw IllegalStateException("activity binder unavailable")
            val wrapped = ShizukuBinderWrapper(binder as IBinder)
            val stub = Class.forName("android.app.IActivityManager\$Stub")
            val am = stub.getMethod("asInterface", IBinder::class.java)
                .invoke(null, wrapped)
                ?: throw IllegalStateException("IActivityManager proxy unavailable")
            val iam = Class.forName("android.app.IActivityManager")
            runningAppProcessesMethod = iam.getMethod("getRunningAppProcesses")
            processMemoryInfoMethod = iam.getMethod(
                "getProcessMemoryInfo",
                IntArray::class.java,
            )
            proxy = am
        }
    }

    private fun resetProxy() {
        proxy = null
        runningAppProcessesMethod = null
        processMemoryInfoMethod = null
    }
}
