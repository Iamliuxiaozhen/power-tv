package com.zitzhen.powertv.ui

import android.app.Application
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zitzhen.powertv.PowerTvApp
import com.zitzhen.powertv.data.model.SystemSnapshot
import com.zitzhen.powertv.data.shizuku.ShizukuManager
import com.zitzhen.powertv.data.shizuku.ShizukuState
import com.zitzhen.powertv.data.system.SystemMonitor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

data class MonitorUiState(
    val snapshot: SystemSnapshot? = null,
    val cpuHistory: List<Float> = emptyList(),
    val gpuHistory: List<Float> = emptyList(),
    val thermalHistory: List<Float> = emptyList(),
    val memoryHistory: List<Float> = emptyList(),
    val isLive: Boolean = false,
)

class MonitorViewModel(application: Application) : AndroidViewModel(application) {
    private val shizuku: ShizukuManager = (application as PowerTvApp).shizuku
    private val monitor = SystemMonitor(application, shizuku)
    private val _uiState = MutableStateFlow(MonitorUiState())
    val uiState: StateFlow<MonitorUiState> = _uiState.asStateFlow()
    val shizukuState: StateFlow<ShizukuState> = shizuku.state

    private var samplingJob: Job? = null
    private var tick = 0

    fun start() {
        if (samplingJob?.isActive == true) return
        tick = 0
        shizuku.refresh()
        samplingJob = viewModelScope.launch {
            while (isActive) {
                val startedAt = SystemClock.elapsedRealtime()
                val includeProcesses = tick % PROCESS_INTERVAL_TICKS == 0
                val snapshot = try {
                    withContext(Dispatchers.IO) { monitor.sample(includeProcesses) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w(TAG, "Sampling failed", error)
                    null
                }

                if (snapshot != null) {
                    publish(snapshot)
                } else {
                    _uiState.update { it.copy(isLive = false) }
                }

                tick++
                val elapsed = SystemClock.elapsedRealtime() - startedAt
                delay(max(MIN_REFRESH_DELAY_MS, REFRESH_INTERVAL_MS - elapsed))
            }
        }
    }

    fun stop() {
        samplingJob?.cancel()
        samplingJob = null
        _uiState.update { it.copy(isLive = false) }
    }

    fun requestShizukuPermission() {
        shizuku.requestPermission()
    }

    fun retryShizukuService() {
        shizuku.retryUserService()
    }

    fun useShizukuBasicMode() {
        shizuku.useShellFallback()
    }

    private fun publish(snapshot: SystemSnapshot) {
        _uiState.update { state ->
            state.copy(
                snapshot = snapshot,
                cpuHistory = append(state.cpuHistory, snapshot.cpu.usagePercent),
                gpuHistory = append(state.gpuHistory, snapshot.gpu.utilizationPercent),
                thermalHistory = append(state.thermalHistory, snapshot.thermal.maxCelsius),
                memoryHistory = append(state.memoryHistory, snapshot.memory.usedPercent),
                isLive = true,
            )
        }
    }

    private fun append(history: List<Float>, value: Float?): List<Float> {
        if (value == null || !value.isFinite()) return history
        return (history + value).takeLast(MAX_HISTORY_POINTS)
    }

    private companion object {
        const val TAG = "MonitorViewModel"
        const val REFRESH_INTERVAL_MS = 1_000L
        const val MIN_REFRESH_DELAY_MS = 250L
        const val PROCESS_INTERVAL_TICKS = 2
        const val MAX_HISTORY_POINTS = 60
    }
}
