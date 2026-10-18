package com.zitzhen.powertv.data.model

data class SystemSnapshot(
    val timestampMillis: Long,
    val device: DeviceInfo,
    val cpu: CpuMetrics,
    val gpu: GpuMetrics,
    val memory: MemoryMetrics,
    val thermal: ThermalMetrics,
    val battery: BatteryMetrics,
    val processes: ProcessSnapshot,
)
