package com.example.autodriver

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
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
    private var pendingOutcome: TripOutcome? = null
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val handler = Handler(Looper.getMainLooper())
    private val closeButtonTemplate: Bitmap? by lazy {
        try {
            assets.open(CLOSE_BUTTON_TEMPLATE_ASSET).use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            Log.d(TAG, "template do botão X não encontrado: ${e.message}")
            null
        }
    }

    /** Runs while an offer is on screen, re-checking on a timer instead of
     * waiting only for new accessibility events from Uber. If the driver
     * closes the offer and immediately leaves the app, no more events from
     * `TARGET_PACKAGE` arrive and the card-gone detection would otherwise
     * never fire, silently dropping that trip's history record. */
    private val offerWatchdog = object : Runnable {
        override fun run() {
            if (!offerActive) return
            maybeRequestOcr()
            handler.postDelayed(this, OCR_THROTTLE_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // The system can start this service on its own (boot, app update, process
        // restart) without MainActivity/SettingsActivity ever running first, so
        // Config must be loaded here too or it stays at default values.
        PreferencesManager.load(this)

        if (Config.showHistoryOverlay) {
            startService(Intent(this, HistoryOverlayService::class.java))
        }

        ShizukuTapManager.ensureListeners()
        ShizukuTapManager.bindService()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
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
        val now = System.currentTimeMillis()
        if (ocrInFlight) {
            // takeScreenshot's callback can apparently get lost (observed after
            // a multi-stroke sweep gesture), leaving this flag stuck forever
            // and silently freezing all detection — self-heal instead of
            // requiring a manual service restart.
            if (now - lastScreenshotAt < OCR_STUCK_TIMEOUT_MS) return
            Log.d(TAG, "OCR parecia travada, destravando")
            ocrInFlight = false
        }
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
                // Recorded exactly once per offer, right when it actually
                // disappears — not when we merely attempt an action on it,
                // since a reject sweep may need several tries to land.
                recordHistory(pendingOutcome ?: TripOutcome.NO_ACTION)
                offerActive = false
                missedOfferChecks = 0
            }
            return
        }
        missedOfferChecks = 0

        if (!offerActive) {
            offerActive = true
            pendingOutcome = null
            // A value/km card left over from the *previous* offer (it stays
            // up to Config.overlayVisibleSeconds, up to 10s) can still be on
            // screen when this new one arrives — and Android silently drops
            // synthetic touches on a view while any other window overlaps it
            // (View.setFilterTouchesWhenObscured), so a stale card here can
            // block every tap aimed at this offer's own close button.
            if (offerShowing) {
                stopService(Intent(this, OverlayCardService::class.java))
                offerShowing = false
            }
            handler.postDelayed(offerWatchdog, OCR_THROTTLE_MS)
        }
        extractValuePerKm(texts)?.let { currentOfferValuePerKm = it }
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
        val autoRejected = maybeAutoReject(texts, debugBitmap, cropTopOffsetPx)
        val autoAccepted = !autoRejected && maybeAutoAccept(texts)
        if (!autoRejected && !autoAccepted) {
            handleOfferTexts(texts)
        }
    }

    private fun handleOfferTexts(texts: List<String>): Boolean {
        if (!Config.showValueCardOverlay) return false

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
     * should stay suppressed on every pass while the decision holds. A
     * destination on the reject list takes priority over the accept
     * threshold: it forces a reject even when the value/km would otherwise
     * qualify for auto-accept, since [onOcrResult] only evaluates
     * [maybeAutoAccept] when this returns false. The close button's X
     * position is stable across offers, but its Y position isn't (taller
     * cards — more stops, longer addresses — push it around), so instead of
     * a single point this sweeps a column of taps starting at the position
     * captured via Settings > "Capturar posição do X" and moving upward
     * until the card closes. */
    private fun maybeAutoReject(texts: List<String>, cardBitmap: Bitmap, cropTopOffsetPx: Int): Boolean {
        if (!Config.rejectEnabled) return false

        val valuePerKm = extractValuePerKm(texts)
        val destinationBlocked = isDestinationBlocked(currentOfferDestination)
        val belowThreshold = valuePerKm != null && valuePerKm <= Config.rejectWhenValueLessOrEqualTo
        if (!destinationBlocked && !belowThreshold) return false

        val reason = if (destinationBlocked) {
            "destino \"$currentOfferDestination\" está na lista de recusar"
        } else {
            "valor/km ($valuePerKm) abaixo do limite"
        }

        val now = System.currentTimeMillis()
        if (now - lastAutoRejectAt >= AUTO_ACTION_THROTTLE_MS) {
            lastAutoRejectAt = now
            closeCurrentOffer(reason, cardBitmap, cropTopOffsetPx)
        }
        pendingOutcome = TripOutcome.REJECTED
        return true
    }

    /** True when [destination] matches an address the driver added to the
     * reject list (Settings > "Recusar" card) — a plain case-insensitive
     * substring match, since entries are free-typed street/neighborhood
     * names rather than exact addresses. */
    private fun isDestinationBlocked(destination: String): Boolean =
        Config.locations.any { blocked -> destination.contains(blocked, ignoreCase = true) }

    /** Closes the current offer by locating the close button's on-screen
     * position with [ButtonImageMatcher] against [closeButtonTemplate] and
     * tapping it — the button's Y position shifts with card height (more
     * stops, longer addresses) while its appearance stays identical
     * everywhere it appears, which is what template matching keys off of.
     * Falls back to the calibrated-position sweep when no template is
     * bundled or no confident match is found this pass. */
    private fun closeCurrentOffer(reason: String, cardBitmap: Bitmap, cropTopOffsetPx: Int) {
        val match = closeButtonTemplate?.let { template ->
            ButtonImageMatcher.findCloseButton(cardBitmap, template)
        }
        if (match != null) {
            val screenX = match.x
            val screenY = match.y + cropTopOffsetPx
            Log.d(TAG, "$reason, fechando por imagem em ($screenX, $screenY)")
            tapAt(screenX, screenY)
            return
        }

        if (Config.closeButtonX < 0f || Config.closeButtonY < 0f) {
            Log.d(TAG, "$reason, mas nenhum método de fechar está disponível")
            return
        }
        Log.d(
            TAG,
            "$reason, botão não encontrado por imagem, varrendo a partir de " +
                "(${Config.closeButtonX}, ${Config.closeButtonY})"
        )
        sweepTapCloseButton(Config.closeButtonX, Config.closeButtonY)
    }

    /** Returns true whenever this offer should be auto-accepted (same
     * suppress-the-overlay contract as [maybeAutoReject]). Requires the accept
     * button position to have been calibrated via Settings — there's no safe
     * geometry-based guess for a full-width bottom button, and tapping the
     * wrong spot here risks accepting a ride the driver never chose. */
    private fun maybeAutoAccept(texts: List<String>): Boolean {
        if (!Config.acceptEnabled) return false

        val valuePerKm = extractValuePerKm(texts) ?: return false
        if (valuePerKm < Config.acceptWhenValueGreaterThan) return false

        val now = System.currentTimeMillis()
        if (now - lastAutoAcceptAt >= AUTO_ACTION_THROTTLE_MS) {
            lastAutoAcceptAt = now
            acceptCurrentOffer(valuePerKm)
        }
        pendingOutcome = TripOutcome.ACCEPTED
        return true
    }

    /** Accepting has no safe geometry-based guess for a full-width bottom
     * button, and tapping the wrong spot risks accepting a ride the driver
     * never chose — so unlike the close button, this only ever taps the
     * position captured via Settings > "Capturar posição do Aceitar". */
    private fun acceptCurrentOffer(valuePerKm: Float) {
        if (Config.acceptButtonX < 0f || Config.acceptButtonY < 0f) {
            Log.d(TAG, "Valor/km ($valuePerKm) acima do limite, mas a posição do Aceitar ainda não foi capturada")
            return
        }
        Log.d(
            TAG,
            "Valor/km ($valuePerKm) acima do limite de aceitar, aceitando automaticamente em " +
                "(${Config.acceptButtonX}, ${Config.acceptButtonY})"
        )
        tapAt(Config.acceptButtonX, Config.acceptButtonY)
    }

    private fun recordHistory(outcome: TripOutcome) {
        val valuePerKm = currentOfferValuePerKm ?: return
        TripHistory.add(TripRecord(valuePerKm, currentOfferDestination, outcome))
    }

    /** Uber always renders the offer's own value/km with a trailing
     * qualifier ("R$1.82/km est." in English, "R$ 1,82/km aprox." in
     * Portuguese), while our own history overlay's rows read plainly as
     * "R$ x,xx/km" with nothing after — so preferring a suffixed match keeps
     * a stale row from that overlay (rendered above the real card and
     * therefore earlier in reading order) from being picked up as if it
     * were the current offer's price. Falls back to a plain match only if
     * no suffixed one is found. */
    private fun extractValuePerKm(texts: List<String>): Float? {
        val suffixed = texts.firstNotNullOfOrNull { text ->
            VALUE_PER_KM_WITH_SUFFIX_REGEX.find(text)?.groupValues?.get(1)
        }
        val raw = suffixed ?: texts.firstNotNullOfOrNull { text ->
            VALUE_PER_KM_REGEX.find(text)?.groupValues?.get(1)
        }
        return raw?.replace(",", ".")?.toFloatOrNull()
    }

    /** The first comma-separated segment is usually just the street name;
     * what follows (number, neighborhood, city) is more useful at a glance. */
    private fun extractDestination(texts: List<String>): String? =
        texts.lastOrNull { ADDRESS_REGEX.containsMatchIn(it.trim()) }
            ?.trim()
            ?.substringAfter(',')
            ?.trim()

    /** Confirmed live: identical coordinates via `adb shell input tap` close
     * Uber's offer card instantly, while dispatchGesture below does nothing
     * at all — Uber silently ignores accessibility-dispatched touches on
     * this card specifically. Shizuku (when authorized) issues that same
     * real shell-level tap from inside the app; dispatchGesture is only a
     * fallback for when Shizuku isn't installed/paired/granted. */
    private fun tapAt(x: Float, y: Float) {
        if (ShizukuTapManager.tap(x, y)) {
            Log.d(TAG, "auto-ação: toque via Shizuku")
            return
        }
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, TAP_DURATION_MS))
            .build()
        val dispatched = dispatchGesture(gesture, null, null)
        Log.d(TAG, "auto-ação: gesto disparado=$dispatched")
    }


    /** Fires a column of short taps at a fixed X, spaced out over time within
     * a single gesture, starting at (startX, startY) — the captured close-
     * button position — and sweeping upward, since the Y position varies with
     * card height but the X position doesn't. */
    private fun sweepTapCloseButton(startX: Float, startY: Float) {
        val builder = GestureDescription.Builder()
        var startTime = 0L
        var y = startY
        val topY = startY - SWEEP_SPAN_PX
        while (y >= topY) {
            val path = Path().apply { moveTo(startX, y) }
            builder.addStroke(GestureDescription.StrokeDescription(path, startTime, TAP_DURATION_MS))
            startTime += SWEEP_STEP_DELAY_MS
            y -= SWEEP_STEP_PX
        }
        val dispatched = dispatchGesture(builder.build(), null, null)
        Log.d(TAG, "auto-reject: varredura disparada=$dispatched")
    }

    override fun onInterrupt() {}

    companion object {
        private const val TAG = "RideAccessibility"
        private const val TARGET_PACKAGE = "com.ubercab.driver"
        private const val OCR_THROTTLE_MS = 700L
        private const val OCR_STUCK_TIMEOUT_MS = 5000L
        private const val CROP_TOP_FRACTION = 0.35f
        private const val OFFER_MISS_TOLERANCE = 3
        private const val AUTO_ACTION_THROTTLE_MS = 1500L
        private const val TAP_DURATION_MS = 100L
        // The close button's X is stable across offer cards; its Y drifts with
        // card height, so auto-reject sweeps upward from the captured position
        // instead of tapping just one point.
        private const val SWEEP_SPAN_PX = 700f
        private const val SWEEP_STEP_PX = 100f
        private const val SWEEP_STEP_DELAY_MS = 150L
        private const val CLOSE_BUTTON_TEMPLATE_ASSET = "close_button_template.png"
        // Substrings robust to common single-character OCR slips on the offer
        // button label ("Selecionar" -> "Seiecionar", "Aceitar" -> "Acaitar")
        // while staying specific enough to avoid matching unrelated screen text.
        // Covers both the Portuguese ("Selecionar", "Aceitar") and English
        // ("Select", "Accept") Uber Driver UI, since the app's language can
        // change on its own (device/account locale) independently of this app.
        private val OFFER_BUTTON_MARKERS = listOf("ecionar", "eitar", "aitar", "elect", "ccept", "atch")
        // Decimal separator varies with the app's language too: "R$ 18,65"
        // (PT) vs "R$18.65" (EN).
        private val PRICE_REGEX = Regex("""R\$\s*(\d+[.,]\d{2})""")
        private val DISTANCE_REGEX = Regex("""(\d+\.\d+)\s*km""")
        private val VALUE_PER_KM_REGEX = Regex("""R\$\s*(\d+[.,]\d{2})\s*/\s*km""")
        private val VALUE_PER_KM_WITH_SUFFIX_REGEX = Regex(
            """R\$\s*(\d+[.,]\d{2})\s*/\s*km\s*(est\.|aprox\.)""",
            RegexOption.IGNORE_CASE
        )
        private val ADDRESS_REGEX = Regex(
            """^(Rua|R\.|Av\.|Avenida|Alameda|Al\.|Rodovia|Rod\.|Estrada|Travessa)\b.*,.*""",
            RegexOption.IGNORE_CASE
        )
    }
}
