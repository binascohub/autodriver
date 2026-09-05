package com.example.autodriver

object Config {
    val locations: MutableList<String> = mutableListOf()
    var acceptWhenValueGreaterThan: Float = 0f
    var rejectWhenValueLessOrEqualTo: Float = 0f
    var overlayVisibleSeconds: Int = 5
    var closeButtonX: Float = -1f
    var closeButtonY: Float = -1f
    var acceptButtonX: Float = -1f
    var acceptButtonY: Float = -1f
    var showHistoryOverlay: Boolean = false
    var showValueCardOverlay: Boolean = true

    /** True when [destination] matches an address the driver added to the
     * reject list (main screen's "Recusar" card) — a plain case-insensitive
     * substring match, since entries are free-typed street/neighborhood
     * names rather than exact addresses. */
    fun isDestinationBlocked(destination: String): Boolean =
        locations.any { blocked -> destination.contains(blocked, ignoreCase = true) }
}
