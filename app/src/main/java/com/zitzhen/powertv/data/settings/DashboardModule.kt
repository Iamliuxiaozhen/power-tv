package com.zitzhen.powertv.data.settings

import androidx.annotation.IdRes
import androidx.annotation.StringRes
import com.zitzhen.powertv.R

/**
 * The dashboard cards that can be reordered and toggled from Settings.
 * [cardIdRes] points to the [com.google.android.material.card.MaterialCardView]
 * in [R.layout.screen_dashboard].
 */
enum class DashboardModule(
    val key: String,
    @StringRes val titleRes: Int,
    @IdRes val cardIdRes: Int,
) {
    HERO("hero", R.string.dashboard_hero_label, R.id.card_hero),
    CPU("cpu", R.string.dashboard_cpu_title, R.id.card_cpu),
    GPU("gpu", R.string.dashboard_gpu_title, R.id.card_gpu),
    THERMAL("thermal", R.string.dashboard_thermal_title, R.id.card_thermal),
    MEMORY("memory", R.string.dashboard_memory_title, R.id.card_memory),
    BATTERY("battery", R.string.dashboard_battery_title, R.id.card_battery),
    DEVICE("device", R.string.dashboard_device_title, R.id.card_device),
    ;

    companion object {
        val DEFAULT_ORDER: List<DashboardModule> = entries.toList()
        val DEFAULT_VISIBLE: Set<DashboardModule> = entries.toSet()

        fun fromKey(key: String): DashboardModule? = entries.firstOrNull { it.key == key }
    }
}
