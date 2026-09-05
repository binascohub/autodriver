package com.example.autodriver

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class TripOutcome { ACCEPTED, REJECTED, NO_ACTION }

data class TripRecord(
    val valuePerKm: Float,
    val destination: String,
    val outcome: TripOutcome,
    val timestamp: Long = System.currentTimeMillis()
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
    row.findViewById<TextView>(R.id.history_time).text =
        SimpleDateFormat("HH:mm:ss", Locale("pt", "BR")).format(Date(record.timestamp))
    row.findViewById<TextView>(R.id.history_value).text =
        String.format(Locale("pt", "BR"), "R$ %.2f/km", record.valuePerKm)
    row.findViewById<TextView>(R.id.history_destination).text = record.destination

    val outcomeView = row.findViewById<TextView>(R.id.history_outcome)
    val outcomeIcon = row.findViewById<ImageView>(R.id.history_outcome_icon)
    val (label, colorRes, iconRes) = when (record.outcome) {
        TripOutcome.ACCEPTED -> Triple("Aceito", R.color.tw_green_400, R.drawable.ic_check_circle)
        TripOutcome.REJECTED -> Triple("Recusado", R.color.tw_red_400, R.drawable.ic_cancel)
        TripOutcome.NO_ACTION -> Triple("Sem ação", R.color.tw_text_secondary, R.drawable.ic_remove)
    }
    val color = ContextCompat.getColor(row.context, colorRes)
    outcomeView.text = label
    outcomeView.setTextColor(color)
    outcomeIcon.setImageResource(iconRes)
    outcomeIcon.setColorFilter(color)
}
