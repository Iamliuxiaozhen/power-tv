package com.zitzhen.powertv.data.system

import android.os.Build
import android.os.SystemClock
import com.zitzhen.powertv.data.model.DeviceInfo

object DeviceInfoSampler {

    fun sample(): DeviceInfo {
        val isEmulator = Build.MODEL.startsWith("sdk_") ||
            Build.HARDWARE.contains("ranchu") ||
            Build.PRODUCT.contains("sdk")
        val manufacturer = when {
            isEmulator -> "Android"
            else -> Build.MANUFACTURER
                .replaceFirstChar { it.titlecase() }
                .takeIf { it.isNotBlank() }
                ?: "Android"
        }
        return DeviceInfo(
            manufacturer = manufacturer,
            model = when {
                isEmulator -> "Emulator"
                Build.MODEL.isBlank() -> "Android device"
                else -> Build.MODEL
            },
            androidVersion = Build.VERSION.RELEASE ?: Build.VERSION.CODENAME,
            sdkInt = Build.VERSION.SDK_INT,
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            hardware = Build.HARDWARE,
            uptimeMillis = SystemClock.elapsedRealtime(),
        )
    }
}
