package com.example.autodriver

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import java.util.Locale

class OverlayCardService : Service() {

    private var overlayView: View? = null
    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private val autoCloseHandler = Handler(Looper.getMainLooper())
    private val autoCloseRunnable = Runnable { stopSelf() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val totalValue = intent?.getFloatExtra(EXTRA_TOTAL_VALUE, 0f) ?: 0f
        val totalKm = intent?.getFloatExtra(EXTRA_TOTAL_KM, 0f) ?: 0f
        val acceptThreshold = intent?.getFloatExtra(EXTRA_ACCEPT_THRESHOLD, 0f) ?: 0f
        val rejectThreshold = intent?.getFloatExtra(EXTRA_REJECT_THRESHOLD, 0f) ?: 0f
        val visibleSeconds = intent?.getIntExtra(EXTRA_VISIBLE_SECONDS, 5) ?: 5
        showCard(totalValue, totalKm, acceptThreshold, rejectThreshold, visibleSeconds)
        activeInstance = this
        return START_NOT_STICKY
    }

    private fun showCard(
        totalValue: Float,
        totalKm: Float,
        acceptThreshold: Float,
        rejectThreshold: Float,
        visibleSeconds: Int
    ) {
        if (overlayView != null) return

        val themedContext = ContextThemeWrapper(this, R.style.OverlayTheme)
        val view = LayoutInflater.from(themedContext).inflate(R.layout.overlay_value_card, null)

        val valuePerKm = if (totalKm > 0f) totalValue / totalKm else 0f
        view.findViewById<TextView>(R.id.overlay_value_per_km).text =
            String.format(Locale("pt", "BR"), "R$ %.2f/km", valuePerKm)
        view.findViewById<TextView>(R.id.overlay_breakdown).text =
            String.format(Locale("pt", "BR"), "R$ %.2f ÷ %.1f km", totalValue, totalKm)
        view.findViewById<View>(R.id.overlay_close_button).setOnClickListener { stopSelf() }

        if (valuePerKm <= rejectThreshold) {
            (view as MaterialCardView).setCardBackgroundColor(
                ContextCompat.getColor(this, R.color.tw_red_600)
            )
        } else if (valuePerKm >= acceptThreshold) {
            (view as MaterialCardView).setCardBackgroundColor(
                ContextCompat.getColor(this, R.color.tw_green_600)
            )
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER
        params.y = -(3f / 2.54f * resources.displayMetrics.densityDpi).toInt()

        windowManager.addView(view, params)
        overlayView = view

        autoCloseHandler.postDelayed(autoCloseRunnable, visibleSeconds * 1000L)
    }

    override fun onDestroy() {
        super.onDestroy()
        autoCloseHandler.removeCallbacks(autoCloseRunnable)
        overlayView?.let { windowManager.removeView(it) }
        overlayView = null
        if (activeInstance == this) activeInstance = null
    }

    companion object {
        const val EXTRA_TOTAL_VALUE = "extra_total_value"
        const val EXTRA_TOTAL_KM = "extra_total_km"
        const val EXTRA_ACCEPT_THRESHOLD = "extra_accept_threshold"
        const val EXTRA_REJECT_THRESHOLD = "extra_reject_threshold"
        const val EXTRA_VISIBLE_SECONDS = "extra_visible_seconds"

        private var activeInstance: OverlayCardService? = null

        /** Same rationale as [HistoryOverlayService.setHidden]: this card also
         * shows a "R$ x,xx/km" line that would otherwise get read back by OCR
         * alongside the real offer card underneath it. */
        fun setHidden(hidden: Boolean) {
            activeInstance?.overlayView?.visibility = if (hidden) View.INVISIBLE else View.VISIBLE
        }
    }
}
