package com.example.autodriver

import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.util.Locale

enum class TripOutcome { ACCEPTED, REJECTED, NO_ACTION }

data class TripRecord(
    val valuePerKm: Float,
    val destination: String,
    val outcome: TripOutcome
)

object TripHistory {
    const val OVERLAY_VISIBLE_COUNT = 3
    private const val MAX_RECORDS = 20

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

/** Binds a [TripRecord] into a view inflated from `item_history_row.xml`. */
fun bindTripRecordRow(row: View, record: TripRecord) {
    row.findViewById<TextView>(R.id.history_value).text =
        String.format(Locale("pt", "BR"), "R$ %.2f/km", record.valuePerKm)
    row.findViewById<TextView>(R.id.history_destination).text = record.destination

    val outcomeView = row.findViewById<TextView>(R.id.history_outcome)
    val (label, colorRes) = when (record.outcome) {
        TripOutcome.ACCEPTED -> "Aceito" to R.color.tw_green_400
        TripOutcome.REJECTED -> "Recusado" to R.color.tw_red_400
        TripOutcome.NO_ACTION -> "Sem ação" to R.color.tw_text_secondary
    }
    outcomeView.text = label
    outcomeView.setTextColor(ContextCompat.getColor(row.context, colorRes))
}
