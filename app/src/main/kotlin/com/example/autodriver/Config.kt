package com.example.autodriver

object Config {
    val locations: MutableList<String> = mutableListOf()
    var acceptEnabled: Boolean = true
    var acceptWhenValueGreaterThan: Float = 0f
    var rejectEnabled: Boolean = true
    var rejectWhenValueLessOrEqualTo: Float = 0f
    var overlayVisibleSeconds: Int = 5
    var closeButtonX: Float = -1f
    var closeButtonY: Float = -1f
    var acceptButtonX: Float = -1f
    var acceptButtonY: Float = -1f
    var showHistoryOverlay: Boolean = false
}
