package com.example.autodriver

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

class HistoryOverlayService : Service() {

    private var overlayView: View? = null
    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        showOverlay()
        TripHistory.onChanged = { refreshRows() }
        refreshRows()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun showOverlay() {
        if (overlayView != null) return

        val themedContext = ContextThemeWrapper(this, R.style.OverlayTheme)
        val view = LayoutInflater.from(themedContext).inflate(R.layout.overlay_history, null)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP
        params.y = resources.displayMetrics.densityDpi / 4

        windowManager.addView(view, params)
        overlayView = view
    }

    private fun refreshRows() {
        val container = overlayView?.findViewById<LinearLayout>(R.id.history_rows_container) ?: return
        val context = container.context
        container.removeAllViews()

        TripHistory.records.forEach { record ->
            val row = LayoutInflater.from(context).inflate(R.layout.item_history_row, container, false)
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
            outcomeView.setTextColor(ContextCompat.getColor(context, colorRes))

            container.addView(row)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        TripHistory.onChanged = null
        overlayView?.let { windowManager.removeView(it) }
        overlayView = null
    }
}
