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
                viewModel.uiState.collect(::render)
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
