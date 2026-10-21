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
}

data class ShizukuState(
    val status: ShizukuStatus = ShizukuStatus.NOT_RUNNING,
    val managerInstalled: Boolean = false,
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

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            binding = false
            if (binder == null || !binder.pingBinder()) {
                onReaderLost(ShizukuStatus.CONNECTING)
                mainHandler.post { refresh() }
                return
            }
            reader = IProcReader.Stub.asInterface(binder)
            publish(ShizukuStatus.CONNECTED)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            binding = false
            reader = null
            publish(ShizukuStatus.CONNECTING)
            mainHandler.post { refresh() }
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
        } else if (!binding) {
            publish(ShizukuStatus.CONNECTING)
            bind()
        }
    }

    fun requestPermission() {
        runCatching { Shizuku.requestPermission(REQUEST_CODE) }
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

    /** Returns the raw `/proc/stat` contents, or null when Shizuku is not ready. */
    fun readCpuStat(): String? = read { it.readCpuStat() }

    /** Returns the encoded process table, or null when Shizuku is not ready. */
    fun readProcessTable(): String? = read { it.readProcessTable() }

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
            publish(ShizukuStatus.NOT_RUNNING)
        }
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
        binding = false
        reader = null
        publish(status)
    }

    private fun publish(status: ShizukuStatus) {
        _state.value = ShizukuState(status = status, managerInstalled = isManagerInstalled())
    }

    private fun isManagerInstalled(): Boolean = runCatching {
        context.packageManager.getPackageInfo(MANAGER_PACKAGE, 0)
        true
    }.getOrDefault(false)

    private companion object {
        const val REQUEST_CODE = 1001
        const val MANAGER_PACKAGE = "moe.shizuku.privileged.api"
        const val DOWNLOAD_URL = "https://shizuku.rikka.app/download/"
    }
}
