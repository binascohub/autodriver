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

    /** True when any of [texts] contains an address on the reject list —
     * checked against every OCR'd line from the offer card, not just the
     * single resolved "destination" (drop-off) string. A driver adding
     * "Colombo" to the list almost certainly wants to catch it showing up
     * as the *pickup* too, not only as the final leg; checking the raw
     * lines directly also sidesteps needing the drop-off address to have
     * been successfully parsed out at all on this OCR pass. */
    fun isAnyTextBlocked(texts: List<String>): Boolean =
        locations.any { blocked -> texts.any { it.contains(blocked, ignoreCase = true) } }

    /** The color a value/km reading should be shown in — shared by the
     * floating alert card and trip history, so a driver glancing back at
     * history sees the same verdict color the card showed live: red when
     * the destination was on the reject list or the rate was at/below the
     * reject threshold, green at/above the accept threshold, amber
     * otherwise. */
    fun cardColorRes(valuePerKm: Float, destinationBlocked: Boolean): Int = when {
        destinationBlocked -> R.color.tw_red_400
        valuePerKm <= rejectWhenValueLessOrEqualTo -> R.color.tw_red_400
        valuePerKm >= acceptWhenValueGreaterThan -> R.color.tw_green_400
        else -> R.color.accent_amber
    }
}
