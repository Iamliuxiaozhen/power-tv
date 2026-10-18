package com.zitzhen.powertv.data.system

import android.content.Context
import android.os.Build
import android.os.HardwarePropertiesManager
import android.os.PowerManager
import com.zitzhen.powertv.data.model.TemperatureKind
import com.zitzhen.powertv.data.model.TemperatureReading
import com.zitzhen.powertv.data.model.ThermalMetrics
import com.zitzhen.powertv.data.model.ThermalStatus
import java.io.File

/**
 * Collects temperatures from the public HardwarePropertiesManager first and
 * falls back to world-readable thermal sysfs nodes. Vendor kernels expose very
 * different sensor names, so unavailable sensors are simply omitted.
 */
class ThermalSampler(
    private val context: Context,
    private val thermalRoot: File = File("/sys/class/thermal"),
) {
    private val hardwareProperties =
        context.getSystemService(HardwarePropertiesManager::class.java)
    private val powerManager = context.getSystemService(PowerManager::class.java)

    fun sample(batteryTemperatureCelsius: Float? = null): ThermalMetrics {
        val readings = mutableListOf<TemperatureReading>()
        val coveredKinds = mutableSetOf<TemperatureKind>()

        addHardwareReading(readings, coveredKinds, TemperatureKind.CPU, HardwarePropertiesManager.DEVICE_TEMPERATURE_CPU)
        addHardwareReading(readings, coveredKinds, TemperatureKind.GPU, HardwarePropertiesManager.DEVICE_TEMPERATURE_GPU)
        addHardwareReading(readings, coveredKinds, TemperatureKind.BATTERY, HardwarePropertiesManager.DEVICE_TEMPERATURE_BATTERY)
        addHardwareReading(readings, coveredKinds, TemperatureKind.SKIN, HardwarePropertiesManager.DEVICE_TEMPERATURE_SKIN)
        addSysfsReadings(readings, coveredKinds)
        if (readings.none { it.kind == TemperatureKind.BATTERY } && batteryTemperatureCelsius != null) {
            readings += TemperatureReading(
                label = "Battery",
                celsius = batteryTemperatureCelsius,
                kind = TemperatureKind.BATTERY,
            )
        }

        return ThermalMetrics(
            readings = readings.distinctBy { it.kind to it.label }.sortedByDescending { it.celsius },
            status = readThermalStatus(),
        )
    }

    private fun addHardwareReading(
        readings: MutableList<TemperatureReading>,
        coveredKinds: MutableSet<TemperatureKind>,
        kind: TemperatureKind,
        deviceType: Int,
    ) {
        val temperature = runCatching {
            hardwareProperties
                ?.getDeviceTemperatures(deviceType, HardwarePropertiesManager.TEMPERATURE_CURRENT)
                ?.filter { it.isFinite() && it > -30f && it < 150f }
                ?.maxOrNull()
        }.getOrNull() ?: return

        val label = when (kind) {
            TemperatureKind.CPU -> "CPU"
            TemperatureKind.GPU -> "GPU"
            TemperatureKind.BATTERY -> "Battery"
            TemperatureKind.SKIN -> "Skin"
            else -> kind.name
        }
        readings += TemperatureReading(label = label, celsius = temperature, kind = kind)
        coveredKinds += kind
    }

    private fun addSysfsReadings(
        readings: MutableList<TemperatureReading>,
        coveredKinds: MutableSet<TemperatureKind>,
    ) {
        val zones = thermalRoot.listFiles()
            ?.filter { it.name.startsWith("thermal_zone") }
            ?.sortedBy { it.name }
            ?: return

        for (zone in zones) {
            if (readings.size >= MAX_READINGS) break
            val type = runCatching { File(zone, "type").readText().trim() }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: continue
            val raw = runCatching { File(zone, "temp").readText().trim().toFloat() }.getOrNull()
                ?: continue
            val celsius = if (raw > 1_000f) raw / 1_000f else raw
            if (!celsius.isFinite() || celsius < -30f || celsius > 150f) continue

            val kind = classify(type)
            if (kind in coveredKinds) continue
            readings += TemperatureReading(label = type, celsius = celsius, kind = kind)
        }
    }

    private fun readThermalStatus(): ThermalStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return ThermalStatus.UNKNOWN
        return runCatching {
            when (powerManager?.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> ThermalStatus.NONE
                PowerManager.THERMAL_STATUS_LIGHT -> ThermalStatus.LIGHT
                PowerManager.THERMAL_STATUS_MODERATE -> ThermalStatus.MODERATE
                PowerManager.THERMAL_STATUS_SEVERE -> ThermalStatus.SEVERE
                PowerManager.THERMAL_STATUS_CRITICAL -> ThermalStatus.CRITICAL
                PowerManager.THERMAL_STATUS_EMERGENCY -> ThermalStatus.EMERGENCY
                PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalStatus.SHUTDOWN
                else -> ThermalStatus.UNKNOWN
            }
        }.getOrDefault(ThermalStatus.UNKNOWN)
    }

    private fun classify(type: String): TemperatureKind {
        val name = type.lowercase()
        return when {
            name.contains("gpu") -> TemperatureKind.GPU
            name.contains("batt") -> TemperatureKind.BATTERY
            name.contains("skin") || name.contains("shell") || name.contains("case") -> TemperatureKind.SKIN
            name.contains("cpu") || name.contains("soc") || name.contains("core") ||
                name.contains("cluster") || name.contains("tsens") || name.contains("ap-") -> TemperatureKind.CPU
            name.contains("board") || name.contains("pmic") || name.contains("charger") -> TemperatureKind.BOARD
            else -> TemperatureKind.OTHER
        }
    }

    private companion object {
        const val MAX_READINGS = 8
    }
}
