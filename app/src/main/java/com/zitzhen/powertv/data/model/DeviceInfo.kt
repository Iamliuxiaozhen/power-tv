package com.zitzhen.powertv.data.model

data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val sdkInt: Int,
    val supportedAbis: List<String>,
    val hardware: String,
    val uptimeMillis: Long,
)
