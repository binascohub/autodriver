package com.example.autodriver

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    private lateinit var localText: TextInputEditText
    private lateinit var addLocalButton: Button
    private lateinit var localListContainer: LinearLayout
    private lateinit var acceptEnabledSwitch: SwitchMaterial
    private lateinit var acceptWhenValue: TextInputEditText
    private lateinit var rejectEnabledSwitch: SwitchMaterial
    private lateinit var rejectWhenValue: TextInputEditText
    private lateinit var overlayPermissionStatus: TextView
    private lateinit var overlayPermissionButton: Button
    private lateinit var accessibilityPermissionStatus: TextView
    private lateinit var accessibilityPermissionButton: Button
    private lateinit var captureAcceptButton: Button
    private lateinit var captureRejectButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (false && !SessionManager.isSignedIn(this)) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)
        PreferencesManager.load(this)

        findViewById<Button>(R.id.menu_button).setOnClickListener { anchor ->
            val popup = PopupMenu(this, anchor)
            popup.menu.add("Configurações")
            popup.menu.add("Sobre")
            popup.setOnMenuItemClickListener { item ->
                when (item.title) {
                    "Configurações" -> startActivity(Intent(this, SettingsActivity::class.java))
                    "Sobre" -> startActivity(Intent(this, AboutActivity::class.java))
                }
                true
            }
            popup.show()
        }

        localText = findViewById(R.id.local_text)
        addLocalButton = findViewById(R.id.add_local_button)
        localListContainer = findViewById(R.id.local_list_container)

        addLocalButton.setOnClickListener {
            val address = localText.text?.toString()?.trim().orEmpty()
            if (address.isNotEmpty()) {
                Config.locations.add(address)
                addLocationToList(address)
                localText.text = null
                PreferencesManager.saveConfig(this)
            }
        }

        acceptEnabledSwitch = findViewById(R.id.accept_enabled_switch)
        acceptWhenValue = findViewById(R.id.accept_when_value)
        rejectEnabledSwitch = findViewById(R.id.reject_enabled_switch)
        rejectWhenValue = findViewById(R.id.reject_when_value)

        captureAcceptButton = findViewById(R.id.capture_accept_button)
        captureRejectButton = findViewById(R.id.capture_reject_button)

        acceptEnabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            Config.acceptEnabled = isChecked
            captureAcceptButton.visibility = if (isChecked) android.view.View.VISIBLE else android.view.View.GONE
            PreferencesManager.saveConfig(this)
        }
        rejectEnabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            Config.rejectEnabled = isChecked
            captureRejectButton.visibility = if (isChecked) android.view.View.VISIBLE else android.view.View.GONE
            PreferencesManager.saveConfig(this)
        }

        captureAcceptButton.setOnClickListener { startCoordinateCapture(CoordinateCaptureService.TARGET_ACCEPT) }
        captureRejectButton.setOnClickListener { startCoordinateCapture(CoordinateCaptureService.TARGET_REJECT) }

        acceptEnabledSwitch.isChecked = Config.acceptEnabled
        rejectEnabledSwitch.isChecked = Config.rejectEnabled
        captureAcceptButton.visibility = if (Config.acceptEnabled) android.view.View.VISIBLE else android.view.View.GONE
        captureRejectButton.visibility = if (Config.rejectEnabled) android.view.View.VISIBLE else android.view.View.GONE
        acceptWhenValue.setText(Config.acceptWhenValueGreaterThan.takeIf { it != 0f }?.toString() ?: "")
        rejectWhenValue.setText(Config.rejectWhenValueLessOrEqualTo.takeIf { it != 0f }?.toString() ?: "")

        Config.locations.forEach { addLocationToList(it) }

        val thresholdWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = saveThresholdsIfValid()
        }
        acceptWhenValue.addTextChangedListener(thresholdWatcher)
        rejectWhenValue.addTextChangedListener(thresholdWatcher)

        overlayPermissionStatus = findViewById(R.id.overlay_permission_status)
        overlayPermissionButton = findViewById(R.id.overlay_permission_button)
        overlayPermissionButton.setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }

        accessibilityPermissionStatus = findViewById(R.id.accessibility_permission_status)
        accessibilityPermissionButton = findViewById(R.id.accessibility_permission_button)
        accessibilityPermissionButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        updateOverlayPermissionStatus()
        updateAccessibilityPermissionStatus()
    }

    private fun startCoordinateCapture(target: String) {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Conceda a permissão de sobreposição primeiro", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, CoordinateCaptureService::class.java)
        intent.putExtra(CoordinateCaptureService.EXTRA_TARGET, target)
        startService(intent)
    }

    private fun saveThresholdsIfValid() {
        val acceptValue = acceptWhenValue.text?.toString()?.toFloatOrNull() ?: 0f
        val rejectValue = rejectWhenValue.text?.toString()?.toFloatOrNull() ?: 0f

        if (rejectValue >= acceptValue) {
            Toast.makeText(
                this,
                "O valor de recusar não pode ser maior ou igual ao valor de aceitar",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        Config.acceptWhenValueGreaterThan = acceptValue
        Config.rejectWhenValueLessOrEqualTo = rejectValue
        PreferencesManager.saveConfig(this)
    }

    override fun onPause() {
        super.onPause()
        PreferencesManager.saveConfig(this)
    }

    private fun addLocationToList(address: String) {
        val item = layoutInflater.inflate(R.layout.item_location, localListContainer, false)
        item.findViewById<TextView>(R.id.location_text).text = "• $address"

        item.findViewById<TextView>(R.id.remove_location_button).setOnClickListener {
            val index = localListContainer.indexOfChild(item)
            if (index >= 0 && index < Config.locations.size) {
                Config.locations.removeAt(index)
            }
            localListContainer.removeView(item)
        }

        localListContainer.addView(item)
    }

    private fun updateOverlayPermissionStatus() {
        val granted = Settings.canDrawOverlays(this)
        overlayPermissionStatus.text = if (granted) "Concedida" else "Não concedida"
        overlayPermissionStatus.setTextColor(
            ContextCompat.getColor(this, if (granted) R.color.tw_green_400 else R.color.tw_red_400)
        )
        overlayPermissionButton.visibility = if (granted) android.view.View.GONE else android.view.View.VISIBLE
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
}
