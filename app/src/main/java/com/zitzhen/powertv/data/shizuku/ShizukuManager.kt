package com.zitzhen.powertv.data.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

enum class ShizukuStatus {
    /** Shizuku older than v11 is not supported. */
    UNSUPPORTED,

    /** The Shizuku binder is absent: not installed or the service is stopped. */
    NOT_RUNNING,

    /** Shizuku is running but this app has not been granted access yet. */
    PERMISSION_REQUIRED,

    /** Access was denied and Shizuku will not show the dialog again. */
    PERMISSION_DENIED,

    /** Access granted; the privileged reader service is being bound. */
    CONNECTING,

    /** The privileged reader service is ready. */
    CONNECTED,

    /**
     * Permission was granted but the privileged reader service could not be
     * started (e.g. ROM bugs that crash Shizuku user services on startup).
     */
    START_FAILED,
}

/** How privileged process data is obtained once Shizuku access is granted. */
enum class ReaderMode {
    /** Spawns a Shizuku user service that reads /proc with shell identity. */
    USER_SERVICE,

    /**
     * Calls ActivityManager directly over a Shizuku-wrapped binder. Spawns no
     * child process, so it survives ROMs where user services crash; per-process
     * CPU usage is unavailable in this mode.
     */
    BINDER,
}

data class ShizukuState(
    val status: ShizukuStatus = ShizukuStatus.NOT_RUNNING,
    val managerInstalled: Boolean = false,
    val mode: ReaderMode = ReaderMode.USER_SERVICE,
    /** True when START_FAILED looks like the known MediaTek/Xiaomi ROM bug. */
    val suspectedRomBug: Boolean = false,
)

/**
 * Owns the Shizuku permission and user-service lifecycle.
 *
 * All public methods are safe to call from the main thread; [readCpuStat] and
 * [readProcessTable] are meant for the IO sampling thread.
 */
class ShizukuManager(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(ShizukuState())
    val state: StateFlow<ShizukuState> = _state.asStateFlow()

    @Volatile
    private var reader: IProcReader? = null

    private var started = false
    private var binding = false

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var mode: ReaderMode =
        if (prefs.getString(KEY_READER_MODE, null) == MODE_VALUE_BINDER) {
            ReaderMode.BINDER
        } else {
            ReaderMode.USER_SERVICE
        }

    private var startWatchdog: Runnable? = null

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        mainHandler.post { refresh() }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        mainHandler.post { onReaderLost(ShizukuStatus.NOT_RUNNING) }
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { _, _ ->
            mainHandler.post { refresh() }
        }

    private val userServiceArgs: Shizuku.UserServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(context.packageName, ShizukuProcService::class.java.name),
        )
            .daemon(false)
            .processNameSuffix("proc")
            .debuggable(false)
            .version(packageVersionCode())
    }

    private val connection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (mode == ReaderMode.BINDER) {
                // The user switched to binder mode while this bind was in
                // flight; drop the connection instead of adopting it.
                binding = false
                runCatching {
                    Shizuku.unbindUserService(
                        userServiceArgs,
                        this@ShizukuManager.connection,
                        true,
                    )
                }
                return
            }
            cancelStartWatchdog()
            binding = false
            if (binder == null || !binder.pingBinder()) {
                reader = null
                publish(ShizukuStatus.START_FAILED)
                return
            }
            reader = IProcReader.Stub.asInterface(binder)
            publish(ShizukuStatus.CONNECTED)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            if (mode == ReaderMode.BINDER) return
            cancelStartWatchdog()
            binding = false
            val wasConnected = reader != null
            reader = null
            if (wasConnected) {
                // The service ran before; it was likely killed and a rebind is
                // worth trying (the watchdog covers a failed restart).
                publish(ShizukuStatus.CONNECTING)
                mainHandler.post { refresh() }
            } else {
                // Disconnected before the binder ever arrived: the service
                // process died during startup.
                publish(ShizukuStatus.START_FAILED)
            }
        }
    }

    fun start() {
        if (started) return
        started = true
        runCatching {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
        }
        refresh()
    }

    /** Re-evaluates binder, permission and service state. */
    fun refresh() {
        if (!started) return

        val supported = runCatching { !Shizuku.isPreV11() }.getOrDefault(false)
        if (!supported) {
            onReaderLost(ShizukuStatus.UNSUPPORTED)
            return
        }

        val binderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!binderAlive) {
            onReaderLost(ShizukuStatus.NOT_RUNNING)
            return
        }

        val granted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        if (!granted) {
            val denied = runCatching {
                Shizuku.shouldShowRequestPermissionRationale()
            }.getOrDefault(false)
            onReaderLost(
                if (denied) ShizukuStatus.PERMISSION_DENIED else ShizukuStatus.PERMISSION_REQUIRED,
            )
            return
        }

        if (reader != null) {
            publish(ShizukuStatus.CONNECTED)
        } else if (mode == ReaderMode.BINDER) {
            // Binder mode needs no service handshake; permission is enough.
            publish(ShizukuStatus.CONNECTED)
        } else if (!binding) {
            publish(ShizukuStatus.CONNECTING)
            bind()
        }
    }

    fun requestPermission() {
        runCatching { Shizuku.requestPermission(REQUEST_CODE) }
    }

    /**
     * Switches to binder-direct basic mode and persists the choice, so devices
     * where the user service can never start stay usable across restarts.
     */
    fun useBinderFallback() {
        cancelStartWatchdog()
        if (binding || reader != null) {
            runCatching {
                Shizuku.unbindUserService(userServiceArgs, connection, true)
            }
            binding = false
            reader = null
        }
        mode = ReaderMode.BINDER
        prefs.edit().putString(KEY_READER_MODE, MODE_VALUE_BINDER).apply()
        refresh()
    }

    /** Switches back to the privileged user service and retries starting it. */
    fun retryUserService() {
        cancelStartWatchdog()
        binding = false
        reader = null
        mode = ReaderMode.USER_SERVICE
        prefs.edit().remove(KEY_READER_MODE).apply()
        refresh()
    }

    @Suppress("DEPRECATION")
    private fun packageVersionCode(): Int = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            info.versionCode
        }
    }.getOrDefault(1)

    /**
     * Returns the raw `/proc/stat` contents, or null when Shizuku is not ready.
     * Always null in binder mode: the binder API cannot read files, so the CPU
     * sampler falls back to whatever the app itself can read.
     */
    fun readCpuStat(): String? =
        if (mode == ReaderMode.BINDER) null else read { it.readCpuStat() }

    /** Returns the encoded process table, or null when Shizuku is not ready. */
    fun readProcessTable(): String? =
        if (mode == ReaderMode.BINDER) {
            ActivityManagerProcessReader.readProcessTable()
        } else {
            read { it.readProcessTable() }
        }

    fun openManager() {
        val managerIntent = context.packageManager
            .getLaunchIntentForPackage(MANAGER_PACKAGE)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (managerIntent != null) {
            runCatching { context.startActivity(managerIntent) }
            return
        }
        val download = Intent(Intent.ACTION_VIEW, Uri.parse(DOWNLOAD_URL))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(download) }
    }

    private fun bind() {
        binding = true
        runCatching {
            Shizuku.bindUserService(userServiceArgs, connection)
        }.onFailure {
            binding = false
            cancelStartWatchdog()
            publish(ShizukuStatus.NOT_RUNNING)
            return
        }
        armStartWatchdog()
    }

    /**
     * The Shizuku starter reports nothing when the user service process dies
     * before delivering its binder (e.g. the MediaTek ROM bug); without a
     * timeout the UI would spin on "connecting" forever.
     */
    private fun armStartWatchdog() {
        cancelStartWatchdog()
        val watchdog = Runnable {
            if (mode == ReaderMode.USER_SERVICE && binding && reader == null) {
                binding = false
                publish(ShizukuStatus.START_FAILED)
            }
        }
        startWatchdog = watchdog
        mainHandler.postDelayed(watchdog, START_TIMEOUT_MS)
    }

    private fun cancelStartWatchdog() {
        startWatchdog?.let { mainHandler.removeCallbacks(it) }
        startWatchdog = null
    }

    private fun read(block: (IProcReader) -> String): String? {
        val service = reader ?: return null
        return try {
            block(service)
        } catch (_: RemoteException) {
            onReadFailed()
            null
        } catch (_: RuntimeException) {
            onReadFailed()
            null
        }
    }

    private fun onReadFailed() {
        binding = false
        reader = null
        mainHandler.post { refresh() }
    }

    private fun onReaderLost(status: ShizukuStatus) {
        cancelStartWatchdog()
        binding = false
        reader = null
        publish(status)
    }

    private fun publish(status: ShizukuStatus) {
        _state.value = ShizukuState(
            status = status,
            managerInstalled = isManagerInstalled(),
            mode = mode,
            suspectedRomBug = status == ShizukuStatus.START_FAILED && isLikelyMediaTekRom(),
        )
    }

    /**
     * The Shizuku user-service startup crash in issue #1198 is specific to
     * MediaTek-based Xiaomi ROMs, so the failure copy can name the cause.
     */
    private fun isLikelyMediaTekRom(): Boolean {
        val parts = buildList {
            add(Build.HARDWARE)
            add(Build.BOARD)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Build.SOC_MANUFACTURER)
                add(Build.SOC_MODEL)
            }
        }
        return parts.any { part ->
            val value = part.lowercase()
            value.contains("mediatek") || value.contains("mtk") || value.startsWith("mt")
        }
    }

    private fun isManagerInstalled(): Boolean = runCatching {
        context.packageManager.getPackageInfo(MANAGER_PACKAGE, 0)
        true
    }.getOrDefault(false)

    private companion object {
        const val REQUEST_CODE = 1001
        const val MANAGER_PACKAGE = "moe.shizuku.privileged.api"
        const val DOWNLOAD_URL = "https://shizuku.rikka.app/download/"
        const val START_TIMEOUT_MS = 8_000L
        const val PREFS_NAME = "powertv"
        const val KEY_READER_MODE = "shizuku_reader_mode"
        const val MODE_VALUE_BINDER = "binder"
    }
}
