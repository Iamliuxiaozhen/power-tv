package com.zitzhen.powertv.data.system

import android.os.Build
import android.os.SystemClock
import com.zitzhen.powertv.data.model.DeviceInfo

object DeviceInfoSampler {

    fun sample(): DeviceInfo {
        val manufacturer = Build.MANUFACTURER
            .replaceFirstChar { it.titlecase() }
            .takeIf { it.isNotBlank() }
            ?: "Android"
        return DeviceInfo(
            manufacturer = manufacturer,
            model = Build.MODEL.ifBlank { "Android device" },
            androidVersion = Build.VERSION.RELEASE ?: Build.VERSION.CODENAME,
            sdkInt = Build.VERSION.SDK_INT,
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            hardware = Build.HARDWARE,
            uptimeMillis = SystemClock.elapsedRealtime(),
        )
    }
}
