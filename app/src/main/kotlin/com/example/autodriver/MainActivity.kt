package com.example.autodriver

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    private lateinit var localText: TextInputEditText
    private lateinit var addLocalButton: Button
    private lateinit var localListContainer: LinearLayout
    private lateinit var acceptWhenValue: TextInputEditText
    private lateinit var rejectWhenValue: TextInputEditText
    private lateinit var overlayPermissionIcon: ImageView
    private lateinit var overlayPermissionStatus: TextView
    private lateinit var overlayPermissionButton: Button
    private lateinit var accessibilityPermissionIcon: ImageView
    private lateinit var accessibilityPermissionStatus: TextView
    private lateinit var accessibilityPermissionButton: Button
    private lateinit var captureAcceptStatus: TextView
    private lateinit var captureRejectStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (false && !SessionManager.isSignedIn(this)) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)
        PreferencesManager.load(this)

        findViewById<ImageButton>(R.id.menu_button).setOnClickListener { anchor ->
            val popup = PopupMenu(this, anchor)
            popup.menu.add("Configurações")
            popup.menu.add("Histórico")
            popup.menu.add("Sobre")
            popup.setOnMenuItemClickListener { item ->
                when (item.title) {
                    "Configurações" -> startActivity(Intent(this, SettingsActivity::class.java))
                    "Histórico" -> startActivity(Intent(this, HistoryActivity::class.java))
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

        acceptWhenValue = findViewById(R.id.accept_when_value)
        rejectWhenValue = findViewById(R.id.reject_when_value)

        captureAcceptStatus = findViewById(R.id.capture_accept_status)
        captureRejectStatus = findViewById(R.id.capture_reject_status)

        updateCaptureStatuses()
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

        overlayPermissionIcon = findViewById(R.id.overlay_permission_icon)
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

        accessibilityPermissionIcon = findViewById(R.id.accessibility_permission_icon)
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
        PreferencesManager.load(this)
        updateCaptureStatuses()
    }

    private fun updateCaptureStatuses() {
        captureAcceptStatus.text = formatCapturedPosition(Config.acceptButtonX, Config.acceptButtonY)
        captureRejectStatus.text = formatCapturedPosition(Config.closeButtonX, Config.closeButtonY)
    }

    private fun formatCapturedPosition(x: Float, y: Float): String =
        if (x >= 0f && y >= 0f) {
            "Posição atual: (${x.toInt()}, ${y.toInt()})"
        } else {
            "Nenhuma posição capturada ainda"
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

        item.findViewById<ImageView>(R.id.remove_location_button).setOnClickListener {
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
        applyPermissionStatus(overlayPermissionIcon, overlayPermissionStatus, overlayPermissionButton, granted)
    }

    private fun updateAccessibilityPermissionStatus() {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val granted = enabledServices.contains("$packageName/${RideAccessibilityService::class.java.name}")
        applyPermissionStatus(accessibilityPermissionIcon, accessibilityPermissionStatus, accessibilityPermissionButton, granted)
    }

    /** Mirrors a permission's state across its status icon/label (a small
     * green check or red cancel telltale, like a dashboard indicator) and
     * its "Conceder Permissão" button, which only needs to be shown while
     * the permission is missing. */
    private fun applyPermissionStatus(icon: ImageView, label: TextView, button: Button, granted: Boolean) {
        val color = ContextCompat.getColor(this, if (granted) R.color.tw_green_400 else R.color.tw_red_400)
        icon.setImageResource(if (granted) R.drawable.ic_check_circle else R.drawable.ic_cancel)
        icon.setColorFilter(color)
        label.text = if (granted) "Concedida" else "Não concedida"
        label.setTextColor(color)
        button.visibility = if (granted) android.view.View.GONE else android.view.View.VISIBLE
    }
}
