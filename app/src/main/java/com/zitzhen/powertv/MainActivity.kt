package com.zitzhen.powertv

import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zitzhen.powertv.data.shizuku.ReaderMode
import com.zitzhen.powertv.data.shizuku.ShizukuState
import com.zitzhen.powertv.data.shizuku.ShizukuStatus
import com.zitzhen.powertv.databinding.ActivityMainBinding
import com.zitzhen.powertv.ui.DashboardBinder
import com.zitzhen.powertv.ui.MonitorUiState
import com.zitzhen.powertv.ui.MonitorViewModel
import com.zitzhen.powertv.ui.ProcessBinder
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var dashboardBinder: DashboardBinder
    private lateinit var processBinder: ProcessBinder
    private val viewModel: MonitorViewModel by viewModels()
    private var currentShizukuState: ShizukuState? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dashboardBinder = DashboardBinder(binding.dashboard)
        processBinder = ProcessBinder(binding.processes)
        dashboardBinder.bind(MonitorUiState())
        showDashboard(true)

        binding.toolbar.menu.findItem(R.id.action_about)?.setOnMenuItemClickListener {
            showAboutDialog()
            true
        }

        binding.processes.shizukuAction.setOnClickListener {
            val state = currentShizukuState ?: return@setOnClickListener
            when (state.status) {
                ShizukuStatus.PERMISSION_REQUIRED -> viewModel.requestShizukuPermission()
                ShizukuStatus.NOT_RUNNING,
                ShizukuStatus.PERMISSION_DENIED,
                -> (application as PowerTvApp).shizuku.openManager()
                ShizukuStatus.CONNECTED -> when (state.mode) {
                    ReaderMode.SHELL -> viewModel.retryShizukuService()
                    ReaderMode.USER_SERVICE -> (application as PowerTvApp).shizuku.openManager()
                }
                ShizukuStatus.START_FAILED -> viewModel.retryShizukuService()
                ShizukuStatus.CONNECTING,
                ShizukuStatus.UNSUPPORTED,
                -> Unit
            }
        }

        binding.processes.shizukuActionSecondary.setOnClickListener {
            val state = currentShizukuState ?: return@setOnClickListener
            if (state.status == ShizukuStatus.START_FAILED) {
                viewModel.useShizukuBasicMode()
            }
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> showDashboard(true)
                R.id.nav_processes -> showDashboard(false)
            }
            true
        }

        onBackPressedDispatcher.addCallback(this) {
            if (binding.processes.root.isVisible) {
                binding.bottomNav.selectedItemId = R.id.nav_dashboard
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch { viewModel.shizukuState.collect(::renderShizuku) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.start()
    }

    override fun onStop() {
        viewModel.stop()
        super.onStop()
    }

    private fun render(state: MonitorUiState) {
        dashboardBinder.bind(state)
        processBinder.bind(state)
    }

    private fun renderShizuku(state: ShizukuState) {
        currentShizukuState = state
        val body = when (state.status) {
            ShizukuStatus.UNSUPPORTED -> R.string.shizuku_body_unsupported
            ShizukuStatus.NOT_RUNNING -> R.string.shizuku_body_not_running
            ShizukuStatus.PERMISSION_REQUIRED -> R.string.shizuku_body_permission
            ShizukuStatus.PERMISSION_DENIED -> R.string.shizuku_body_denied
            ShizukuStatus.CONNECTING -> R.string.shizuku_body_connecting
            ShizukuStatus.CONNECTED -> when (state.mode) {
                ReaderMode.SHELL -> R.string.shizuku_body_connected_basic
                ReaderMode.USER_SERVICE -> R.string.shizuku_body_connected
            }
            ShizukuStatus.START_FAILED -> if (state.suspectedRomBug) {
                R.string.shizuku_body_start_failed_mediatek
            } else {
                R.string.shizuku_body_start_failed
            }
        }
        binding.processes.shizukuBody.setText(body)

        val action = when (state.status) {
            ShizukuStatus.PERMISSION_REQUIRED -> R.string.shizuku_action_grant
            ShizukuStatus.PERMISSION_DENIED -> R.string.shizuku_action_manage
            ShizukuStatus.CONNECTED -> when (state.mode) {
                ReaderMode.SHELL -> R.string.shizuku_action_retry_service
                ReaderMode.USER_SERVICE -> R.string.shizuku_action_manage
            }
            ShizukuStatus.START_FAILED -> R.string.shizuku_action_retry
            ShizukuStatus.NOT_RUNNING -> if (state.managerInstalled) {
                R.string.shizuku_action_open
            } else {
                R.string.shizuku_action_install
            }
            ShizukuStatus.CONNECTING -> R.string.shizuku_action_connecting
            ShizukuStatus.UNSUPPORTED -> null
        }
        binding.processes.shizukuAction.apply {
            isVisible = action != null
            if (action != null) {
                setText(action)
                isEnabled = state.status != ShizukuStatus.CONNECTING
            }
        }

        val secondary = when (state.status) {
            ShizukuStatus.START_FAILED -> R.string.shizuku_action_basic_mode
            else -> null
        }
        binding.processes.shizukuActionSecondary.apply {
            isVisible = secondary != null
            if (secondary != null) {
                setText(secondary)
            }
        }
    }

    private fun showDashboard(dashboardVisible: Boolean) {
        binding.dashboard.root.isVisible = dashboardVisible
        binding.processes.root.isVisible = !dashboardVisible
    }

    private fun showAboutDialog() {
        val version = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull()
        val message = buildString {
            append(getString(R.string.about_body))
            if (!version.isNullOrBlank()) {
                append("\n\n")
                append(getString(R.string.about_version, version))
            }
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.about_title)
            .setMessage(message)
            .setPositiveButton(R.string.about_ok, null)
            .show()
    }
}
