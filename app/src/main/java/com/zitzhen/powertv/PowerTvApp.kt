package com.zitzhen.powertv

import android.app.Application
import com.zitzhen.powertv.data.settings.ModuleSettingsRepository
import com.zitzhen.powertv.data.shizuku.ShizukuManager

/**
 * Owns the process-wide Shizuku connection so the privileged reader survives
 * Activity re-creation.
 */
class PowerTvApp : Application() {
    val shizuku: ShizukuManager by lazy { ShizukuManager(this) }
    val moduleSettings: ModuleSettingsRepository by lazy { ModuleSettingsRepository(this) }

    override fun onCreate() {
        super.onCreate()
        shizuku.start()
    }
}
