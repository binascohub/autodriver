package com.example.autodriver

import android.content.Context

object PreferencesManager {
    private const val PREFS = "autodriver_config"
    private const val KEY_LOCATIONS = "locations"
    private const val KEY_ACCEPT_THRESHOLD = "accept_threshold"
    private const val KEY_REJECT_THRESHOLD = "reject_threshold"
    private const val KEY_OVERLAY_VISIBLE_SECONDS = "overlay_visible_seconds"
    private const val KEY_CLOSE_BUTTON_X = "close_button_x"
    private const val KEY_CLOSE_BUTTON_Y = "close_button_y"
    private const val KEY_ACCEPT_BUTTON_X = "accept_button_x"
    private const val KEY_ACCEPT_BUTTON_Y = "accept_button_y"
    private const val KEY_SHOW_HISTORY_OVERLAY = "show_history_overlay"
    private const val KEY_SHOW_VALUE_CARD_OVERLAY = "show_value_card_overlay"
    private const val LOCATION_DELIMITER = "\u0000"

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        Config.locations.clear()
        prefs.getString(KEY_LOCATIONS, null)
            ?.split(LOCATION_DELIMITER)
            ?.filter { it.isNotEmpty() }
            ?.let { Config.locations.addAll(it) }

        Config.acceptWhenValueGreaterThan = prefs.getFloat(KEY_ACCEPT_THRESHOLD, Config.acceptWhenValueGreaterThan)
        Config.rejectWhenValueLessOrEqualTo = prefs.getFloat(KEY_REJECT_THRESHOLD, Config.rejectWhenValueLessOrEqualTo)
        Config.overlayVisibleSeconds = prefs.getInt(KEY_OVERLAY_VISIBLE_SECONDS, Config.overlayVisibleSeconds)
        Config.closeButtonX = prefs.getFloat(KEY_CLOSE_BUTTON_X, Config.closeButtonX)
        Config.closeButtonY = prefs.getFloat(KEY_CLOSE_BUTTON_Y, Config.closeButtonY)
        Config.acceptButtonX = prefs.getFloat(KEY_ACCEPT_BUTTON_X, Config.acceptButtonX)
        Config.acceptButtonY = prefs.getFloat(KEY_ACCEPT_BUTTON_Y, Config.acceptButtonY)
        Config.showHistoryOverlay = prefs.getBoolean(KEY_SHOW_HISTORY_OVERLAY, Config.showHistoryOverlay)
        Config.showValueCardOverlay = prefs.getBoolean(KEY_SHOW_VALUE_CARD_OVERLAY, Config.showValueCardOverlay)
    }

    fun saveConfig(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LOCATIONS, Config.locations.joinToString(LOCATION_DELIMITER))
            .putFloat(KEY_ACCEPT_THRESHOLD, Config.acceptWhenValueGreaterThan)
            .putFloat(KEY_REJECT_THRESHOLD, Config.rejectWhenValueLessOrEqualTo)
            .putInt(KEY_OVERLAY_VISIBLE_SECONDS, Config.overlayVisibleSeconds)
            .putFloat(KEY_CLOSE_BUTTON_X, Config.closeButtonX)
            .putFloat(KEY_CLOSE_BUTTON_Y, Config.closeButtonY)
            .putFloat(KEY_ACCEPT_BUTTON_X, Config.acceptButtonX)
            .putFloat(KEY_ACCEPT_BUTTON_Y, Config.acceptButtonY)
            .putBoolean(KEY_SHOW_HISTORY_OVERLAY, Config.showHistoryOverlay)
            .putBoolean(KEY_SHOW_VALUE_CARD_OVERLAY, Config.showValueCardOverlay)
            .apply()
    }
}
