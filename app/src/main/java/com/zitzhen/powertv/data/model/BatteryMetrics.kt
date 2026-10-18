package com.zitzhen.powertv.data.model

enum class BatteryState {
    CHARGING,
    DISCHARGING,
    FULL,
    NOT_CHARGING,
    UNKNOWN,
}

data class BatteryMetrics(
    val levelPercent: Int?,
    val temperatureCelsius: Float?,
    val voltageMillivolts: Int?,
    val currentMilliamps: Int?,
    val state: BatteryState,
    val pluggedIn: Boolean,
    val health: String?,
    val technology: String?,
)
