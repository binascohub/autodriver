package com.example.autodriver

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class RideAccessibilityService : AccessibilityService() {

    private var offerShowing = false
    private var offerActive = false
    private var missedOfferChecks = 0
    private var lastScreenshotAt = 0L
    private var lastAutoRejectAt = 0L
    private var lastAutoAcceptAt = 0L
    private var ocrInFlight = false
    private var currentOfferValuePerKm: Float? = null
    private var currentOfferDestination: String = "Destino desconhecido"
    private var currentOfferRecorded = false
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // The system can start this service on its own (boot, app update, process
        // restart) without MainActivity/SettingsActivity ever running first, so
        // Config must be loaded here too or it stays at default values.
        PreferencesManager.load(this)

        if (Config.showHistoryOverlay) {
            startService(Intent(this, HistoryOverlayService::class.java))
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName != TARGET_PACKAGE) return

        // The offer card (button, price, R$/km, distance) is rendered without
        // accessible text nodes, so every screen change in the target app is a
        // cue to check it with OCR rather than something we can read from the
        // accessibility tree.
        maybeRequestOcr()
    }

    private fun maybeRequestOcr() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (ocrInFlight) return
        val now = System.currentTimeMillis()
        if (now - lastScreenshotAt < OCR_THROTTLE_MS) return
        lastScreenshotAt = now
        ocrInFlight = true

        try {
            requestScreenshot()
        } catch (e: SecurityException) {
            ocrInFlight = false
            Log.d(TAG, "takeScreenshot sem permissão: ${e.message}")
        }
    }

    private fun requestScreenshot() {
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                val hardwareBitmap = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                result.hardwareBuffer.close()
                if (hardwareBitmap == null) {
                    ocrInFlight = false
                    return
                }
                val fullBitmap = hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false)
                hardwareBitmap.recycle()

                // Taller offer cards (multiple stops, longer addresses) push their
                // header/close-button row above the screen's exact midpoint, so
                // the analyzed region starts higher than a plain bottom half to
                // make sure the whole card is always captured.
                val cropTopOffsetPx = (fullBitmap.height * CROP_TOP_FRACTION).toInt()
                val bottomHalf = Bitmap.createBitmap(
                    fullBitmap,
                    0,
                    cropTopOffsetPx,
                    fullBitmap.width,
                    fullBitmap.height - cropTopOffsetPx
                )
                fullBitmap.recycle()

                recognizer.process(InputImage.fromBitmap(bottomHalf, 0))
                    .addOnSuccessListener { visionText ->
                        onOcrResult(visionText.textBlocks.flatMap { it.lines }, cropTopOffsetPx, bottomHalf)
                    }
                    .addOnFailureListener {
                        ocrInFlight = false
                        Log.d(TAG, "OCR falhou: ${it.message}")
                    }
            }

            override fun onFailure(errorCode: Int) {
                ocrInFlight = false
                Log.d(TAG, "takeScreenshot falhou: $errorCode")
            }
        })
    }

    private fun onOcrResult(lines: List<Text.Line>, cropTopOffsetPx: Int, debugBitmap: Bitmap) {
        ocrInFlight = false
        val texts = lines.map { it.text }
        Log.d(TAG, "OCR lines=$texts")

        val hasOfferButton = texts.any { text ->
            OFFER_BUTTON_MARKERS.any { marker -> text.contains(marker, ignoreCase = true) }
        }
        if (!hasOfferButton) {
            // A single OCR pass occasionally misreads the button text (e.g.
            // "Acaitar") even while the offer is still on screen, so the card
            // is only closed after a few consecutive misses, not on the first one.
            missedOfferChecks++
            if (offerActive && missedOfferChecks >= OFFER_MISS_TOLERANCE) {
                Log.d(TAG, "botão de oferta sumiu, fechando card")
                if (offerShowing) {
                    stopService(Intent(this, OverlayCardService::class.java))
                    offerShowing = false
                }
                if (!currentOfferRecorded) {
                    recordHistory(TripOutcome.NO_ACTION)
                }
                offerActive = false
                missedOfferChecks = 0
            }
            return
        }
        missedOfferChecks = 0

        if (!offerActive) {
            offerActive = true
            currentOfferRecorded = false
        }
        texts.firstNotNullOfOrNull { text -> VALUE_PER_KM_REGEX.find(text)?.groupValues?.get(1) }
            ?.replace(",", ".")?.toFloatOrNull()
            ?.let { currentOfferValuePerKm = it }
        extractDestination(texts)?.let { currentOfferDestination = it }

        // TEMPORARY: dump the exact analyzed frame so the close-button tap
        // coordinates can be calibrated against a real screenshot.
        try {
            openFileOutput("debug_offer.png", MODE_PRIVATE).use { out ->
                debugBitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
        } catch (e: Exception) {
            Log.d(TAG, "debug save falhou: ${e.message}")
        }

        // If we're about to auto-accept/reject, don't show our own overlay:
        // Android silently drops touches (even synthetic ones) on security-
        // sensitive buttons when another window is present on screen at all
        // (View.setFilterTouchesWhenObscured), so our card could be the very
        // thing preventing the tap from registering.
        val autoRejected = maybeAutoReject(texts)
        val autoAccepted = !autoRejected && maybeAutoAccept(texts)
        if (!autoRejected && !autoAccepted) {
            handleOfferTexts(texts)
        }
    }

    private fun handleOfferTexts(texts: List<String>): Boolean {
        val totalValue = texts
            .filterNot { it.contains("/km", ignoreCase = true) }
            .firstNotNullOfOrNull { text ->
                PRICE_REGEX.find(text.trim())?.groupValues?.get(1)?.replace(",", ".")?.toFloatOrNull()
            } ?: return false

        val totalKm = texts
            .flatMap { text -> DISTANCE_REGEX.findAll(text).map { it.groupValues[1] } }
            .mapNotNull { it.toFloatOrNull() }
            .sum()
        if (totalKm <= 0f) return false

        Log.d(TAG, "Oferta detectada: R$$totalValue / ${totalKm}km")

        val intent = Intent(this, OverlayCardService::class.java)
        intent.putExtra(OverlayCardService.EXTRA_TOTAL_VALUE, totalValue)
        intent.putExtra(OverlayCardService.EXTRA_TOTAL_KM, totalKm)
        intent.putExtra(OverlayCardService.EXTRA_ACCEPT_THRESHOLD, Config.acceptWhenValueGreaterThan)
        intent.putExtra(OverlayCardService.EXTRA_REJECT_THRESHOLD, Config.rejectWhenValueLessOrEqualTo)
        intent.putExtra(OverlayCardService.EXTRA_VISIBLE_SECONDS, Config.overlayVisibleSeconds)
        startService(intent)
        offerShowing = true
        return true
    }

    /** Returns true whenever this offer should be auto-rejected, regardless of
     * whether the tap actually fired this pass (throttled) — the overlay
     * should stay suppressed on every pass while the decision holds. Requires
     * the close-button position to be calibrated via Settings > "Capturar
     * posição do X" — there is no geometry-based guess, since a guessed tap
     * risks landing on the wrong control entirely. */
    private fun maybeAutoReject(texts: List<String>): Boolean {
        if (!Config.rejectEnabled) return false
        if (Config.closeButtonX < 0f || Config.closeButtonY < 0f) return false

        val valuePerKm = texts.firstNotNullOfOrNull { text ->
            VALUE_PER_KM_REGEX.find(text)?.groupValues?.get(1)
        }?.replace(",", ".")?.toFloatOrNull() ?: return false
        if (valuePerKm > Config.rejectWhenValueLessOrEqualTo) return false

        val now = System.currentTimeMillis()
        if (now - lastAutoRejectAt >= AUTO_ACTION_THROTTLE_MS) {
            lastAutoRejectAt = now
            Log.d(
                TAG,
                "Valor/km ($valuePerKm) abaixo do limite de recusa, recusando automaticamente em " +
                    "(${Config.closeButtonX}, ${Config.closeButtonY})"
            )
            tapAt(Config.closeButtonX, Config.closeButtonY)
            recordHistory(TripOutcome.REJECTED)
        }
        return true
    }

    /** Returns true whenever this offer should be auto-accepted (same
     * suppress-the-overlay contract as [maybeAutoReject]). Requires the accept
     * button position to have been calibrated via Settings — there's no safe
     * geometry-based guess for a full-width bottom button, and tapping the
     * wrong spot here risks accepting a ride the driver never chose. */
    private fun maybeAutoAccept(texts: List<String>): Boolean {
        if (!Config.acceptEnabled) return false
        if (Config.acceptButtonX < 0f || Config.acceptButtonY < 0f) return false

        val valuePerKm = texts.firstNotNullOfOrNull { text ->
            VALUE_PER_KM_REGEX.find(text)?.groupValues?.get(1)
        }?.replace(",", ".")?.toFloatOrNull() ?: return false
        if (valuePerKm < Config.acceptWhenValueGreaterThan) return false

        val now = System.currentTimeMillis()
        if (now - lastAutoAcceptAt >= AUTO_ACTION_THROTTLE_MS) {
            lastAutoAcceptAt = now
            Log.d(
                TAG,
                "Valor/km ($valuePerKm) acima do limite de aceitar, aceitando automaticamente em " +
                    "(${Config.acceptButtonX}, ${Config.acceptButtonY})"
            )
            tapAt(Config.acceptButtonX, Config.acceptButtonY)
            recordHistory(TripOutcome.ACCEPTED)
        }
        return true
    }

    private fun recordHistory(outcome: TripOutcome) {
        val valuePerKm = currentOfferValuePerKm ?: return
        TripHistory.add(TripRecord(valuePerKm, currentOfferDestination, outcome))
        currentOfferRecorded = true
    }

    private fun extractDestination(texts: List<String>): String? =
        texts.lastOrNull { ADDRESS_REGEX.containsMatchIn(it.trim()) }?.trim()

    private fun tapAt(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, TAP_DURATION_MS))
            .build()
        val dispatched = dispatchGesture(gesture, null, null)
        Log.d(TAG, "auto-ação: gesto disparado=$dispatched")
    }

    override fun onInterrupt() {}

    companion object {
        private const val TAG = "RideAccessibility"
        private const val TARGET_PACKAGE = "com.ubercab.driver"
        private const val OCR_THROTTLE_MS = 700L
        private const val CROP_TOP_FRACTION = 0.35f
        private const val OFFER_MISS_TOLERANCE = 3
        private const val AUTO_ACTION_THROTTLE_MS = 1500L
        private const val TAP_DURATION_MS = 50L
        // Substrings robust to common single-character OCR slips on the offer
        // button label ("Selecionar" -> "Seiecionar", "Aceitar" -> "Acaitar")
        // while staying specific enough to avoid matching unrelated screen text.
        private val OFFER_BUTTON_MARKERS = listOf("ecionar", "eitar", "aitar")
        private val PRICE_REGEX = Regex("""R\$\s*(\d+,\d{2})""")
        private val DISTANCE_REGEX = Regex("""(\d+\.\d+)\s*km""")
        private val VALUE_PER_KM_REGEX = Regex("""R\$\s*(\d+,\d{2})\s*/\s*km""")
        private val ADDRESS_REGEX = Regex(
            """^(Rua|R\.|Av\.|Avenida|Alameda|Al\.|Rodovia|Rod\.|Estrada|Travessa)\b.*,.*""",
            RegexOption.IGNORE_CASE
        )
    }
}
