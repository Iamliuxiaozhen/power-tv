package com.zitzhen.powertv.data.system

import android.content.Context
import com.zitzhen.powertv.data.model.ProcessSnapshot
import com.zitzhen.powertv.data.model.SystemSnapshot
import com.zitzhen.powertv.data.model.TemperatureKind
import com.zitzhen.powertv.data.model.TemperatureReading
import com.zitzhen.powertv.data.proc.CpuSampler
import com.zitzhen.powertv.data.proc.MemorySampler
import com.zitzhen.powertv.data.proc.ProcessSampler

/**
 * Aggregates all samplers into one immutable UI state. Must be called from a
 * background thread; file and broadcast reads are performed synchronously.
 */
class SystemMonitor(context: Context) {
    private val appContext = context.applicationContext
    private val cpuSampler = CpuSampler()
    private val memorySampler = MemorySampler(appContext)
    private val batterySampler = BatterySampler(appContext)
    private val thermalSampler = ThermalSampler(appContext)
    private val gpuSampler = GpuSampler()
    private val processSampler = ProcessSampler(appContext)
    private val deviceInfo = DeviceInfoSampler.sample()

    private var processes = ProcessSnapshot(
        processes = emptyList(),
        limitedVisibility = true,
        sampledAtMillis = 0L,
    )

    fun sample(includeProcesses: Boolean): SystemSnapshot {
        val battery = batterySampler.sample()
        val thermal = thermalSampler.sample(battery.temperatureCelsius)
        if (includeProcesses) {
            processes = processSampler.sample()
        }

        return SystemSnapshot(
            timestampMillis = System.currentTimeMillis(),
            device = deviceInfo.copy(uptimeMillis = DeviceInfoSampler.sample().uptimeMillis),
            cpu = cpuSampler.sample(),
            gpu = gpuSampler.sample(thermal),
            memory = memorySampler.sample(),
            thermal = thermal,
            battery = battery,
            processes = processes,
        )
    }

    fun sampleProcesses(): ProcessSnapshot {
        processes = processSampler.sample()
        return processes
    }
}
