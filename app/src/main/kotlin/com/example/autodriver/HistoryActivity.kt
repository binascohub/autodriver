package com.example.autodriver

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class HistoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        findViewById<Button>(R.id.back_button).setOnClickListener { finish() }
        renderHistory()
    }

    private fun renderHistory() {
        val container = findViewById<LinearLayout>(R.id.history_list_container)
        val emptyLabel = findViewById<TextView>(R.id.history_empty_label)
        container.removeAllViews()

        if (TripHistory.records.isEmpty()) {
            emptyLabel.visibility = View.VISIBLE
            return
        }
        emptyLabel.visibility = View.GONE

        TripHistory.records.forEach { record ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_history_row, container, false)
            bindTripRecordRow(row, record)
            container.addView(row)
        }
    }
}
