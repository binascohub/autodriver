package com.example.autodriver

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    private lateinit var visibleSecondsSlider: Slider
    private lateinit var visibleSecondsLabel: TextView
    private lateinit var accessibilityPermissionIcon: ImageView
    private lateinit var accessibilityPermissionStatus: TextView
    private lateinit var accessibilityPermissionButton: Button
    private lateinit var historyOverlaySwitch: SwitchMaterial
    private lateinit var valueCardOverlaySwitch: SwitchMaterial

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<ImageButton>(R.id.back_button).setOnClickListener { finish() }

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

        accessibilityPermissionIcon = findViewById(R.id.accessibility_permission_icon)
        accessibilityPermissionStatus = findViewById(R.id.accessibility_permission_status)
        accessibilityPermissionButton = findViewById(R.id.accessibility_permission_button)
        accessibilityPermissionButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

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
    }

    private fun updateAccessibilityPermissionStatus() {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val granted = enabledServices.contains("$packageName/${RideAccessibilityService::class.java.name}")

        val color = ContextCompat.getColor(this, if (granted) R.color.tw_green_400 else R.color.tw_red_400)
        accessibilityPermissionIcon.setImageResource(if (granted) R.drawable.ic_check_circle else R.drawable.ic_cancel)
        accessibilityPermissionIcon.setColorFilter(color)
        accessibilityPermissionStatus.text = if (granted) "Concedida" else "Não concedida"
        accessibilityPermissionStatus.setTextColor(color)
        accessibilityPermissionButton.visibility = if (granted) android.view.View.GONE else android.view.View.VISIBLE
    }
}
