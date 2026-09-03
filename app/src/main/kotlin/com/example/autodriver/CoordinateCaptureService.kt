package com.example.autodriver

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast

class CoordinateCaptureService : Service() {

    private var overlayView: View? = null
    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val target = intent?.getStringExtra(EXTRA_TARGET) ?: TARGET_REJECT
        showCaptureOverlay(target)
        return START_NOT_STICKY
    }

    private fun showCaptureOverlay(target: String) {
        if (overlayView != null) return

        val themedContext = ContextThemeWrapper(this, R.style.OverlayTheme)
        val view = LayoutInflater.from(themedContext).inflate(R.layout.overlay_coordinate_capture, null)

        view.findViewById<Button>(R.id.capture_cancel_button).setOnClickListener { stopSelf() }

        view.findViewById<View>(R.id.capture_touch_area).setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                saveCapturedCoordinate(target, event.rawX, event.rawY)
                stopSelf()
            }
            true
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            0,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START

        windowManager.addView(view, params)
        overlayView = view
    }

    private fun saveCapturedCoordinate(target: String, x: Float, y: Float) {
        if (target == TARGET_ACCEPT) {
            Config.acceptButtonX = x
            Config.acceptButtonY = y
        } else {
            Config.closeButtonX = x
            Config.closeButtonY = y
        }
        PreferencesManager.saveConfig(this)
        Toast.makeText(this, "Posição salva: (${x.toInt()}, ${y.toInt()})", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayView?.let { windowManager.removeView(it) }
        overlayView = null
    }

    companion object {
        const val EXTRA_TARGET = "extra_target"
        const val TARGET_ACCEPT = "accept"
        const val TARGET_REJECT = "reject"
    }
}
