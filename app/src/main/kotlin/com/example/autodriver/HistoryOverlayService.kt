package com.example.autodriver

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout

class HistoryOverlayService : Service() {

    private var overlayView: View? = null
    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        showOverlay()
        TripHistory.onChanged = { refreshRows() }
        refreshRows()
        activeInstance = this
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

        TripHistory.records.take(TripHistory.OVERLAY_VISIBLE_COUNT).forEach { record ->
            val row = LayoutInflater.from(context).inflate(R.layout.item_history_row, container, false)
            bindTripRecordRow(row, record)
            container.addView(row)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        TripHistory.onChanged = null
        overlayView?.let { windowManager.removeView(it) }
        overlayView = null
        if (activeInstance == this) activeInstance = null
    }

    companion object {
        private var activeInstance: HistoryOverlayService? = null

        /** Hides this overlay for the brief window a screenshot is taken for
         * OCR — otherwise its own "R$ x,xx/km" rows from past offers get read
         * back as if they were the current one, since they render above the
         * offer card and are picked up before it in reading order. */
        fun setHidden(hidden: Boolean) {
            activeInstance?.overlayView?.visibility = if (hidden) View.INVISIBLE else View.VISIBLE
        }
    }
}
