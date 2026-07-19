package com.example.autodriver

object Config {
    val locations: MutableList<String> = mutableListOf()
    var acceptEnabled: Boolean = true
    var acceptWhenValueGreaterThan: Float = 0f
    var rejectEnabled: Boolean = true
    var rejectWhenValueLessOrEqualTo: Float = 0f
    var overlayVisibleSeconds: Int = 5
}
