package com.example.autodriver

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    private lateinit var visibleSecondsSlider: Slider
    private lateinit var visibleSecondsLabel: TextView
    private lateinit var accessibilityPermissionStatus: TextView
    private lateinit var accessibilityPermissionButton: Button
    private lateinit var shizukuStatus: TextView
    private lateinit var shizukuPermissionButton: Button
    private lateinit var historyOverlaySwitch: SwitchMaterial
    private lateinit var valueCardOverlaySwitch: SwitchMaterial

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<Button>(R.id.back_button).setOnClickListener { finish() }

        valueCardOverlaySwitch = findViewById(R.id.value_card_overlay_switch)
        valueCardOverlaySwitch.isChecked = Config.showValueCardOverlay
        valueCardOverlaySwitch.setOnCheckedChangeListener { _, isChecked ->
            Config.showValueCardOverlay = isChecked
            PreferencesManager.saveConfig(this)
        }

        visibleSecondsSlider = findViewById(R.id.visible_seconds_slider)
        visibleSecondsLabel = findViewById(R.id.visible_seconds_label)
        PreferencesManager.load(this)
        visibleSecondsSlider.value = Config.overlayVisibleSeconds.toFloat()
        visibleSecondsLabel.text = "Tempo visível: ${Config.overlayVisibleSeconds}s"
        visibleSecondsSlider.addOnChangeListener { _, value, _ ->
            Config.overlayVisibleSeconds = value.toInt()
            visibleSecondsLabel.text = "Tempo visível: ${value.toInt()}s"
            PreferencesManager.saveConfig(this)
        }

        findViewById<Button>(R.id.test_overlay_button).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Conceda a permissão de sobreposição primeiro", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = Intent(this, OverlayCardService::class.java)
            intent.putExtra(OverlayCardService.EXTRA_TOTAL_VALUE, 10.96f)
            intent.putExtra(OverlayCardService.EXTRA_TOTAL_KM, 3.2f + 2.6f)
            intent.putExtra(OverlayCardService.EXTRA_ACCEPT_THRESHOLD, Config.acceptWhenValueGreaterThan)
            intent.putExtra(OverlayCardService.EXTRA_REJECT_THRESHOLD, Config.rejectWhenValueLessOrEqualTo)
            intent.putExtra(OverlayCardService.EXTRA_VISIBLE_SECONDS, visibleSecondsSlider.value.toInt())
            startService(intent)
        }

        accessibilityPermissionStatus = findViewById(R.id.accessibility_permission_status)
        accessibilityPermissionButton = findViewById(R.id.accessibility_permission_button)
        accessibilityPermissionButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        shizukuStatus = findViewById(R.id.shizuku_status)
        shizukuPermissionButton = findViewById(R.id.shizuku_permission_button)
        shizukuPermissionButton.setOnClickListener {
            if (ShizukuTapManager.isAvailable()) {
                ShizukuTapManager.requestPermission()
            } else {
                Toast.makeText(this, "Abra o app Shizuku e toque em Start primeiro", Toast.LENGTH_LONG).show()
            }
        }
        ShizukuTapManager.ensureListeners()
        ShizukuTapManager.onStateChanged = { runOnUiThread { updateShizukuStatus() } }

        historyOverlaySwitch = findViewById(R.id.history_overlay_switch)
        historyOverlaySwitch.isChecked = Config.showHistoryOverlay
        historyOverlaySwitch.setOnCheckedChangeListener { _, isChecked ->
            Config.showHistoryOverlay = isChecked
            PreferencesManager.saveConfig(this)
            if (isChecked) {
                if (Settings.canDrawOverlays(this)) {
                    startService(Intent(this, HistoryOverlayService::class.java))
                } else {
                    Toast.makeText(this, "Conceda a permissão de sobreposição primeiro", Toast.LENGTH_SHORT).show()
                    historyOverlaySwitch.isChecked = false
                    Config.showHistoryOverlay = false
                    PreferencesManager.saveConfig(this)
                }
            } else {
                stopService(Intent(this, HistoryOverlayService::class.java))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateAccessibilityPermissionStatus()
        updateShizukuStatus()
    }

    private fun updateAccessibilityPermissionStatus() {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val granted = enabledServices.contains("$packageName/${RideAccessibilityService::class.java.name}")

        accessibilityPermissionStatus.text = if (granted) "Concedida" else "Não concedida"
        accessibilityPermissionStatus.setTextColor(
            ContextCompat.getColor(this, if (granted) R.color.tw_green_400 else R.color.tw_red_400)
        )
        accessibilityPermissionButton.visibility = if (granted) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun updateShizukuStatus() {
        val running = ShizukuTapManager.isAvailable()
        if (ShizukuTapManager.hasPermission()) {
            ShizukuTapManager.bindService()
        }
        // checkSelfPermission() alone isn't a reliable signal on this setup —
        // the user service actually being bound is the only real proof the
        // tap path will work, so the button stays visible until that happens.
        val bound = ShizukuTapManager.isServiceBound()

        shizukuStatus.text = when {
            !running -> "Não iniciado"
            !bound -> "Sem permissão"
            else -> "Concedida"
        }
        shizukuStatus.setTextColor(
            ContextCompat.getColor(this, if (bound) R.color.tw_green_400 else R.color.tw_red_400)
        )
        shizukuPermissionButton.visibility = if (bound) android.view.View.GONE else android.view.View.VISIBLE
    }
}
