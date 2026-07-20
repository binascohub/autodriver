package com.example.autodriver

import android.content.Context

object PreferencesManager {
    private const val PREFS = "autodriver_config"
    private const val KEY_LOCATIONS = "locations"
    private const val KEY_ACCEPT_ENABLED = "accept_enabled"
    private const val KEY_ACCEPT_THRESHOLD = "accept_threshold"
    private const val KEY_REJECT_ENABLED = "reject_enabled"
    private const val KEY_REJECT_THRESHOLD = "reject_threshold"
    private const val KEY_OVERLAY_VISIBLE_SECONDS = "overlay_visible_seconds"
    private const val LOCATION_DELIMITER = "\u0000"

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        Config.locations.clear()
        prefs.getString(KEY_LOCATIONS, null)
            ?.split(LOCATION_DELIMITER)
            ?.filter { it.isNotEmpty() }
            ?.let { Config.locations.addAll(it) }

        Config.acceptEnabled = prefs.getBoolean(KEY_ACCEPT_ENABLED, Config.acceptEnabled)
        Config.rejectEnabled = prefs.getBoolean(KEY_REJECT_ENABLED, Config.rejectEnabled)
        Config.acceptWhenValueGreaterThan = prefs.getFloat(KEY_ACCEPT_THRESHOLD, Config.acceptWhenValueGreaterThan)
        Config.rejectWhenValueLessOrEqualTo = prefs.getFloat(KEY_REJECT_THRESHOLD, Config.rejectWhenValueLessOrEqualTo)
        Config.overlayVisibleSeconds = prefs.getInt(KEY_OVERLAY_VISIBLE_SECONDS, Config.overlayVisibleSeconds)
    }

    fun saveConfig(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LOCATIONS, Config.locations.joinToString(LOCATION_DELIMITER))
            .putBoolean(KEY_ACCEPT_ENABLED, Config.acceptEnabled)
            .putBoolean(KEY_REJECT_ENABLED, Config.rejectEnabled)
            .putFloat(KEY_ACCEPT_THRESHOLD, Config.acceptWhenValueGreaterThan)
            .putFloat(KEY_REJECT_THRESHOLD, Config.rejectWhenValueLessOrEqualTo)
            .putInt(KEY_OVERLAY_VISIBLE_SECONDS, Config.overlayVisibleSeconds)
            .apply()
    }
}
