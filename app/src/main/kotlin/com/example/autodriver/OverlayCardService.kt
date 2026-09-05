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
import android.util.Log
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
        val valuePerKm = intent?.getFloatExtra(EXTRA_VALUE_PER_KM, 0f) ?: 0f
        val acceptThreshold = intent?.getFloatExtra(EXTRA_ACCEPT_THRESHOLD, 0f) ?: 0f
        val rejectThreshold = intent?.getFloatExtra(EXTRA_REJECT_THRESHOLD, 0f) ?: 0f
        val visibleSeconds = intent?.getIntExtra(EXTRA_VISIBLE_SECONDS, 5) ?: 5
        val destination = intent?.getStringExtra(EXTRA_DESTINATION).orEmpty()
        val destinationBlocked = intent?.getBooleanExtra(EXTRA_DESTINATION_BLOCKED, false) ?: false
        showCard(valuePerKm, acceptThreshold, rejectThreshold, visibleSeconds, destination, destinationBlocked)
        return START_NOT_STICKY
    }

    private fun showCard(
        valuePerKm: Float,
        acceptThreshold: Float,
        rejectThreshold: Float,
        visibleSeconds: Int,
        destination: String,
        destinationBlocked: Boolean
    ) {
        if (overlayView != null) return

        val themedContext = ContextThemeWrapper(this, R.style.OverlayTheme)
        val view = LayoutInflater.from(themedContext).inflate(R.layout.overlay_value_card, null)

        view.findViewById<TextView>(R.id.overlay_value_per_km).text =
            String.format(Locale("pt", "BR"), "%.2f", valuePerKm)
        view.findViewById<TextView>(R.id.overlay_destination).text = destination
        view.findViewById<View>(R.id.overlay_reject_warning).visibility =
            if (destinationBlocked) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.overlay_close_button).setOnClickListener { stopSelf() }

        // The numeral itself lights up amber/red/green like a taxi meter's
        // digits — the card housing always stays the same dark bezel,
        // instead of flooding the whole card with a flat alert color. A
        // blocked destination overrides the value/km coloring entirely: the
        // whole card reads red, since that decision takes priority over
        // whatever the rate itself would otherwise suggest.
        val stateColor = when {
            destinationBlocked -> R.color.tw_red_400
            valuePerKm <= rejectThreshold -> R.color.tw_red_400
            valuePerKm >= acceptThreshold -> R.color.tw_green_400
            else -> R.color.accent_amber
        }
        Log.d(TAG, "showCard: valuePerKm=$valuePerKm destinationBlocked=$destinationBlocked stateColor=$stateColor")
        val stateColorInt = ContextCompat.getColor(this, stateColor)
        view.findViewById<TextView>(R.id.overlay_value_per_km).setTextColor(stateColorInt)
        (view as MaterialCardView).setStrokeColor(stateColorInt)

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
    }

    companion object {
        private const val TAG = "OverlayCardService"
        const val EXTRA_VALUE_PER_KM = "extra_value_per_km"
        const val EXTRA_ACCEPT_THRESHOLD = "extra_accept_threshold"
        const val EXTRA_REJECT_THRESHOLD = "extra_reject_threshold"
        const val EXTRA_VISIBLE_SECONDS = "extra_visible_seconds"
        const val EXTRA_DESTINATION = "extra_destination"
        const val EXTRA_DESTINATION_BLOCKED = "extra_destination_blocked"
    }
}
