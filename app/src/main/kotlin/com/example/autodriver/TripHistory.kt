package com.example.autodriver

enum class TripOutcome { ACCEPTED, REJECTED, NO_ACTION }

data class TripRecord(
    val valuePerKm: Float,
    val destination: String,
    val outcome: TripOutcome
)

object TripHistory {
    private const val MAX_RECORDS = 3

    val records: MutableList<TripRecord> = mutableListOf()
    var onChanged: (() -> Unit)? = null

    fun add(record: TripRecord) {
        records.add(0, record)
        while (records.size > MAX_RECORDS) {
            records.removeAt(records.size - 1)
        }
        onChanged?.invoke()
    }
}
