package com.zitzhen.powertv.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.color.MaterialColors
import com.zitzhen.powertv.R
import com.zitzhen.powertv.data.model.BatteryState
import com.zitzhen.powertv.data.model.SystemSnapshot
import com.zitzhen.powertv.data.model.ThermalStatus
import com.zitzhen.powertv.databinding.ItemCoreBinding
import com.zitzhen.powertv.databinding.ItemInfoRowBinding
import com.zitzhen.powertv.databinding.ScreenDashboardBinding
import kotlin.math.roundToInt

/** Binds [MonitorUiState] to the dashboard's Material 3 cards. */
class DashboardBinder(private val binding: ScreenDashboardBinding) {

    private enum class PillTone { NEUTRAL, PRIMARY, WARNING }

    private data class CoreCell(val root: View, val binding: ItemCoreBinding)

    private val context = binding.root.context
    private val inflater = LayoutInflater.from(context)
    private var coreCells: List<CoreCell> = emptyList()
    private var deviceRows: List<ItemInfoRowBinding> = emptyList()

    fun bind(state: MonitorUiState) {
        val snapshot = state.snapshot
        if (snapshot == null) {
            bindEmpty(state)
            return
        }

        bindHero(state, snapshot)
        bindCpu(state, snapshot)
        bindGpu(state, snapshot)
        bindThermal(state, snapshot)
        bindMemory(state, snapshot)
        bindBattery(snapshot)
        bindDevice(snapshot)
    }

    private fun bindEmpty(state: MonitorUiState) {
        binding.heroModel.text = "—"
        binding.heroMeta.text = ""
        binding.heroTimestamp.text = ""
        binding.heroLiveLabel.text = context.getString(
            if (state.isLive) R.string.status_live else R.string.status_paused,
        )
        binding.heroLiveDot.alpha = if (state.isLive) 1f else 0.35f

        binding.cpuValue.text = "—"
        binding.cpuSubtitle.text = ""
        binding.cpuFreq.text = ""
        binding.cpuChart.setValues(emptyList())
        binding.cpuCoresContainer.removeAllViews()
        binding.cpuNote.isVisible = false
        coreCells = emptyList()

        binding.gpuValue.text = "—"
        binding.gpuSubtitle.text = ""
        binding.gpuMaxFreq.text = ""
        binding.gpuChart.setValues(emptyList())
        binding.gpuSourcePill.isVisible = false
        binding.gpuNote.isVisible = true

        binding.thermalValue.text = "—"
        binding.thermalSubtitle.text = ""
        binding.thermalStatusPill.text = context.getString(R.string.thermal_status_unknown)
        binding.thermalChart.setValues(emptyList())
        binding.thermalSensors.removeAllViews()
        binding.thermalEmpty.isVisible = true

        binding.memoryPercent.text = "—"
        binding.memorySummary.text = "—"
        binding.memoryProgress.setProgressCompat(0, false)
        binding.memoryCached.text = "—"
        binding.memoryAvailable.text = "—"
        binding.memorySwap.text = "—"

        binding.batteryValue.text = "—"
        binding.batterySubtitle.text = ""
        binding.batteryStatePill.text = context.getString(R.string.dashboard_battery_unknown)
        binding.batteryTemperature.text = "—"
        binding.batteryVoltage.text = "—"
        binding.batteryCurrent.text = "—"

        binding.deviceRows.removeAllViews()
        deviceRows = emptyList()
    }

    private fun bindHero(state: MonitorUiState, snapshot: SystemSnapshot) {
        binding.heroModel.text = "${snapshot.device.manufacturer} ${snapshot.device.model}"
        binding.heroMeta.text = context.getString(
            R.string.dashboard_hero_meta,
            snapshot.device.androidVersion,
            snapshot.device.supportedAbis.firstOrNull() ?: "—",
            Formatters.uptime(snapshot.device.uptimeMillis),
        )
        binding.heroTimestamp.text = context.getString(
            R.string.dashboard_hero_timestamp,
            Formatters.timeOfDay(snapshot.timestampMillis),
        )
        binding.heroLiveLabel.text = context.getString(
            if (state.isLive) R.string.status_live else R.string.status_paused,
        )
        binding.heroLiveDot.alpha = if (state.isLive) 1f else 0.35f
    }

    private fun bindCpu(state: MonitorUiState, snapshot: SystemSnapshot) {
        val cpu = snapshot.cpu
        binding.cpuValue.text = Formatters.percent(cpu.usagePercent)
        binding.cpuSubtitle.text = cpu.model ?: context.getString(R.string.dashboard_cpu_model_unknown)
        binding.cpuFreq.text = cpu.maxFreqMhz?.let {
            context.getString(R.string.dashboard_cpu_freq_max, Formatters.frequency(it))
        } ?: ""
        binding.cpuChart.setValues(state.cpuHistory)
        binding.cpuNote.isVisible = cpu.usagePercent == null

        if (coreCells.size != cpu.cores.size) {
            rebuildCoreCells(cpu.cores.size)
        }
        cpu.cores.forEachIndexed { index, core ->
            val cell = coreCells.getOrNull(index) ?: return@forEachIndexed
            cell.binding.coreName.text = context.getString(R.string.dashboard_core_name, core.index)
            cell.binding.coreFreq.apply {
                text = when {
                    !core.online -> context.getString(R.string.dashboard_core_offline)
                    core.currentFreqMhz != null -> Formatters.compactFrequency(core.currentFreqMhz)
                    else -> ""
                }
                isVisible = text.isNotEmpty()
            }
            cell.binding.coreUsage.text = if (core.online) {
                Formatters.percent(core.usagePercent)
            } else {
                "—"
            }
            val progress = core.usagePercent?.roundToInt()?.coerceIn(0, 100) ?: 0
            cell.binding.coreProgress.setProgressCompat(progress, false)
            cell.binding.coreProgress.setIndicatorColor(LoadPalette.color(context, core.usagePercent))
            cell.root.alpha = if (core.online) 1f else 0.45f
        }
    }

    private fun rebuildCoreCells(coreCount: Int) {
        binding.cpuCoresContainer.removeAllViews()
        coreCells = emptyList()
        val cells = mutableListOf<CoreCell>()
        var row: LinearLayout? = null

        repeat(coreCount) { index ->
            if (index % 2 == 0) {
                row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                }
                binding.cpuCoresContainer.addView(row)
            }
            val cellBinding = ItemCoreBinding.inflate(inflater, row, false)
            val cellView = cellBinding.root
            row?.addView(
                cellView,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            cells += CoreCell(cellView, cellBinding)
        }
        if (coreCount % 2 != 0) {
            row?.addView(
                View(context),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
        }
        coreCells = cells
    }

    private fun bindGpu(state: MonitorUiState, snapshot: SystemSnapshot) {
        val gpu = snapshot.gpu
        binding.gpuValue.text = Formatters.percent(gpu.utilizationPercent)
        binding.gpuSubtitle.text = buildString {
            append(
                if (gpu.name.equals("GPU", ignoreCase = true)) {
                    context.getString(R.string.dashboard_gpu_title)
                } else {
                    context.getString(R.string.dashboard_gpu_name, gpu.name)
                },
            )
            gpu.currentFreqMhz?.let {
                append(context.getString(R.string.dot_separator))
                append(Formatters.frequency(it))
            }
        }
        binding.gpuMaxFreq.text = gpu.maxFreqMhz?.let {
            context.getString(R.string.dashboard_gpu_max_freq, Formatters.frequency(it))
        } ?: ""
        binding.gpuChart.setValues(state.gpuHistory)
        binding.gpuSourcePill.isVisible = gpu.available
        binding.gpuNote.isVisible = gpu.utilizationPercent == null
    }

    private fun bindThermal(state: MonitorUiState, snapshot: SystemSnapshot) {
        val thermal = snapshot.thermal
        val peak = thermal.maxCelsius
        binding.thermalValue.text = Formatters.temperature(peak)
        binding.thermalSubtitle.text = if (thermal.readings.isEmpty()) {
            ""
        } else {
            context.getString(
                R.string.dashboard_thermal_subtitle,
                context.resources.getQuantityString(
                    R.plurals.dashboard_thermal_sensor_count,
                    thermal.readings.size,
                    thermal.readings.size,
                ),
                Formatters.temperature(peak),
            )
        }
        binding.thermalStatusPill.text = context.getString(thermalStatusLabel(thermal.status))
        applyPill(
            binding.thermalStatusPill,
            if (thermal.status.isWarning()) PillTone.WARNING else PillTone.PRIMARY,
        )
        binding.thermalChart.setValues(state.thermalHistory)
        binding.thermalEmpty.isVisible = thermal.readings.isEmpty()

        binding.thermalSensors.removeAllViews()
        thermal.readings.take(5).forEach { reading ->
            val row = ItemInfoRowBinding.inflate(inflater, binding.thermalSensors, false)
            row.infoLabel.text = reading.label
            row.infoValue.text = Formatters.temperature(reading.celsius)
            binding.thermalSensors.addView(row.root)
        }
    }

    private fun bindMemory(state: MonitorUiState, snapshot: SystemSnapshot) {
        val memory = snapshot.memory
        binding.memoryPercent.text = Formatters.percent(memory.usedPercent)
        binding.memorySummary.text = context.getString(
            R.string.dashboard_memory_summary,
            Formatters.bytes(memory.usedBytes),
            Formatters.bytes(memory.totalBytes),
        )
        binding.memoryProgress.setProgressCompat(memory.usedPercent.roundToInt(), false)
        binding.memoryProgress.setIndicatorColor(LoadPalette.color(context, memory.usedPercent))
        binding.memoryCached.text = Formatters.bytes(memory.cachedBytes)
        binding.memoryAvailable.text = Formatters.bytes(memory.availableBytes)
        binding.memorySwap.text = context.getString(
            R.string.dashboard_memory_swap_value,
            Formatters.bytes(memory.swapUsedBytes),
            Formatters.bytes(memory.swapTotalBytes),
        )
    }

    private fun bindBattery(snapshot: SystemSnapshot) {
        val battery = snapshot.battery
        binding.batteryValue.text = battery.levelPercent?.let { "$it%" } ?: "—"
        binding.batteryStatePill.text = context.getString(batteryStateLabel(battery.state))
        val low = battery.levelPercent != null &&
            battery.levelPercent <= 20 &&
            battery.state != BatteryState.CHARGING &&
            battery.state != BatteryState.FULL
        applyPill(
            binding.batteryStatePill,
            when {
                low -> PillTone.WARNING
                battery.state == BatteryState.CHARGING -> PillTone.PRIMARY
                else -> PillTone.NEUTRAL
            },
        )
        binding.batterySubtitle.text = listOfNotNull(battery.technology, battery.health)
            .joinToString(context.getString(R.string.dot_separator))
        binding.batteryTemperature.text = Formatters.temperature(battery.temperatureCelsius)
        binding.batteryVoltage.text = Formatters.voltage(battery.voltageMillivolts)
        binding.batteryCurrent.text = Formatters.current(battery.currentMilliamps)
    }

    private fun bindDevice(snapshot: SystemSnapshot) {
        if (deviceRows.isEmpty()) {
            deviceRows = List(6) {
                ItemInfoRowBinding.inflate(inflater, binding.deviceRows, false).also { row ->
                    binding.deviceRows.addView(row.root)
                }
            }
        }
        val device = snapshot.device
        val model = if (device.model.startsWith(device.manufacturer, ignoreCase = true)) {
            device.model
        } else {
            "${device.manufacturer} ${device.model}"
        }
        val values = listOf(
            context.getString(R.string.dashboard_device_model) to model,
            context.getString(R.string.dashboard_device_android) to
                "${device.androidVersion} (API ${device.sdkInt})",
            context.getString(R.string.dashboard_device_hardware) to device.hardware,
            context.getString(R.string.dashboard_device_abi) to
                (device.supportedAbis.firstOrNull() ?: "—"),
            context.getString(R.string.dashboard_device_uptime) to
                Formatters.uptime(device.uptimeMillis),
            context.getString(R.string.dashboard_device_cores) to context.getString(
                R.string.dashboard_device_cores_value,
                snapshot.cpu.onlineCores,
                snapshot.cpu.cores.size,
            ),
        )
        values.forEachIndexed { index, (label, value) ->
            deviceRows[index].infoLabel.text = label
            deviceRows[index].infoValue.text = value
        }
    }

    private fun applyPill(view: TextView, tone: PillTone) {
        val background = when (tone) {
            PillTone.NEUTRAL -> R.drawable.bg_pill_surface
            PillTone.PRIMARY -> R.drawable.bg_pill_primary
            PillTone.WARNING -> R.drawable.bg_pill_warning
        }
        val textColorAttr = when (tone) {
            PillTone.NEUTRAL -> com.google.android.material.R.attr.colorOnSurfaceVariant
            PillTone.PRIMARY -> com.google.android.material.R.attr.colorOnPrimaryContainer
            PillTone.WARNING -> com.google.android.material.R.attr.colorOnErrorContainer
        }
        view.setBackgroundResource(background)
        view.setTextColor(MaterialColors.getColor(view, textColorAttr))
    }

    private fun batteryStateLabel(state: BatteryState): Int = when (state) {
        BatteryState.CHARGING -> R.string.dashboard_battery_charging
        BatteryState.DISCHARGING -> R.string.dashboard_battery_discharging
        BatteryState.FULL -> R.string.dashboard_battery_full
        BatteryState.NOT_CHARGING -> R.string.dashboard_battery_not_charging
        BatteryState.UNKNOWN -> R.string.dashboard_battery_unknown
    }

    private fun thermalStatusLabel(status: ThermalStatus): Int = when (status) {
        ThermalStatus.NONE -> R.string.thermal_status_none
        ThermalStatus.LIGHT -> R.string.thermal_status_light
        ThermalStatus.MODERATE -> R.string.thermal_status_moderate
        ThermalStatus.SEVERE -> R.string.thermal_status_severe
        ThermalStatus.CRITICAL -> R.string.thermal_status_critical
        ThermalStatus.EMERGENCY -> R.string.thermal_status_emergency
        ThermalStatus.SHUTDOWN -> R.string.thermal_status_shutdown
        ThermalStatus.UNKNOWN -> R.string.thermal_status_unknown
    }

    private fun ThermalStatus.isWarning(): Boolean = when (this) {
        ThermalStatus.SEVERE,
        ThermalStatus.CRITICAL,
        ThermalStatus.EMERGENCY,
        ThermalStatus.SHUTDOWN,
        -> true
        else -> false
    }
}
