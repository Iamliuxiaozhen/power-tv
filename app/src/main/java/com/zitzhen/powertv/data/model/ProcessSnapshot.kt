package com.zitzhen.powertv.data.model

/** A single process visible to the app through `/proc`. */
data class ProcessInfo(
    val pid: Int,
    val name: String,
    val state: String,
    val uid: Int?,
    val memoryBytes: Long,
    val cpuPercent: Float?,
    val isOwnProcess: Boolean,
)

data class ProcessSnapshot(
    val processes: List<ProcessInfo>,
    val limitedVisibility: Boolean,
    val sampledAtMillis: Long,
) {
    val count: Int
        get() = processes.size
}
