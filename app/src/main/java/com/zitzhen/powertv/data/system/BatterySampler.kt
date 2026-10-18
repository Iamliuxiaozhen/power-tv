package com.zitzhen.powertv.data.system

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.zitzhen.powertv.data.model.BatteryMetrics
import com.zitzhen.powertv.data.model.BatteryState

class BatterySampler(private val context: Context) {

    fun sample(): BatteryMetrics {
        val batteryManager = context.getSystemService(BatteryManager::class.java)
        val intent = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()

        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val levelPercent = if (level >= 0 && scale > 0) level * 100 / scale else null

        val temperature = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?.takeUnless { it == Int.MIN_VALUE }
            ?.let { it / 10f }
        val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
            ?.takeIf { it > 0 }
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0

        val currentMicroamps = runCatching {
            batteryManager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        }.getOrNull()?.takeUnless { it == Long.MIN_VALUE || it == 0L }
        val currentMilliamps = currentMicroamps?.let { (it / 1_000L).toInt() }

        val health = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
            ?.takeIf { it >= 0 }
            ?.let(::healthLabel)

        return BatteryMetrics(
            levelPercent = levelPercent,
            temperatureCelsius = temperature,
            voltageMillivolts = voltage,
            currentMilliamps = currentMilliamps,
            state = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> BatteryState.CHARGING
                BatteryManager.BATTERY_STATUS_DISCHARGING -> BatteryState.DISCHARGING
                BatteryManager.BATTERY_STATUS_FULL -> BatteryState.FULL
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> BatteryState.NOT_CHARGING
                else -> BatteryState.UNKNOWN
            },
            pluggedIn = plugged != 0,
            health = health,
            technology = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY),
        )
    }

    private fun healthLabel(value: Int): String = when (value) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
        BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
        else -> "Unknown"
    }
}
