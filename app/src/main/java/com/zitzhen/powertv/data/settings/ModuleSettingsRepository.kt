package com.zitzhen.powertv.data.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists the dashboard module order and visibility to SharedPreferences.
 * Defaults (no stored data) are the natural order with every module visible.
 */
class ModuleSettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadOrder(): List<DashboardModule> {
        val raw = prefs.getString(KEY_ORDER, null) ?: return DashboardModule.DEFAULT_ORDER
        val order = raw.split(",").mapNotNull { DashboardModule.fromKey(it) }
        val missing = DashboardModule.DEFAULT_ORDER.filter { it !in order }
        return order + missing
    }

    fun loadVisible(): Set<DashboardModule> {
        val raw = prefs.getStringSet(KEY_VISIBLE, null)
            ?: return DashboardModule.DEFAULT_VISIBLE
        return raw.mapNotNull { DashboardModule.fromKey(it) }.toSet()
            .ifEmpty { DashboardModule.DEFAULT_VISIBLE }
    }

    fun save(order: List<DashboardModule>, visible: Set<DashboardModule>) {
        prefs.edit()
            .putString(KEY_ORDER, order.joinToString(",") { it.key })
            .putStringSet(KEY_VISIBLE, visible.map { it.key }.toSet())
            .apply()
    }

    fun reset() {
        prefs.edit().remove(KEY_ORDER).remove(KEY_VISIBLE).apply()
    }

    private companion object {
        const val PREFS_NAME = "module_settings"
        const val KEY_ORDER = "order"
        const val KEY_VISIBLE = "visible"
    }
}
