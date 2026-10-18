package com.zitzhen.powertv.data.model

/** Device thermal state as reported by the platform (Android 10+). */
enum class ThermalStatus {
    NONE,
    LIGHT,
    MODERATE,
    SEVERE,
    CRITICAL,
    EMERGENCY,
    SHUTDOWN,
    UNKNOWN,
}

enum class TemperatureKind {
    CPU,
    GPU,
    BATTERY,
    SKIN,
    BOARD,
    OTHER,
}

data class TemperatureReading(
    val label: String,
    val celsius: Float,
    val kind: TemperatureKind,
)

data class ThermalMetrics(
    val readings: List<TemperatureReading>,
    val status: ThermalStatus,
) {
    val maxCelsius: Float?
        get() = readings.maxOfOrNull { it.celsius }

    fun readingFor(kind: TemperatureKind): TemperatureReading? =
        readings.filter { it.kind == kind }.maxByOrNull { it.celsius }
}
