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
import android.view.accessibility.AccessibilityNodeInfo
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class RideAccessibilityService : AccessibilityService() {

    private var offerShowing = false
    private var lastScreenshotAt = 0L
    private var lastAutoRejectAt = 0L
    private var ocrInFlight = false
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName != TARGET_PACKAGE) return

        val texts = mutableListOf<String>()
        windows.forEach { window -> window.root?.let { collectTexts(it, texts) } }
        if (texts.isEmpty()) rootInActiveWindow?.let { collectTexts(it, texts) }

        val hasOfferButton = texts.any { text ->
            OFFER_BUTTON_TEXTS.any { text.trim().equals(it, ignoreCase = true) }
        }

        if (!hasOfferButton) {
            if (offerShowing) {
                Log.d(TAG, "botão de oferta sumiu, fechando card")
                stopService(Intent(this, OverlayCardService::class.java))
                offerShowing = false
            }
            return
        }

        // The offer card (price, R$/km, distance) is rendered without accessible
        // text nodes, so it always needs an OCR pass on the bottom half of the
        // screen — both to display the overlay and to locate the close button.
        handleOfferTexts(texts)
        maybeRequestOcr()
    }

    private fun maybeRequestOcr() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (ocrInFlight) return
        val now = System.currentTimeMillis()
        if (now - lastScreenshotAt < 700L) return
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

                val cropTopOffsetPx = fullBitmap.height / 2
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
                        ocrInFlight = false
                        val lines = visionText.textBlocks.flatMap { block -> block.lines }
                        Log.d(TAG, "OCR lines=${lines.map { it.text }}")
                        handleOfferTexts(lines.map { it.text })
                        maybeAutoReject(lines, cropTopOffsetPx)
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

    private fun maybeAutoReject(lines: List<Text.Line>, cropTopOffsetPx: Int) {
        if (!Config.rejectEnabled) return

        val valuePerKm = lines.firstNotNullOfOrNull { line ->
            VALUE_PER_KM_REGEX.find(line.text)?.groupValues?.get(1)
        }?.replace(",", ".")?.toFloatOrNull() ?: return
        if (valuePerKm > Config.rejectWhenValueLessOrEqualTo) return

        // The offer's product name and close button share the card's top row,
        // so the topmost recognized OCR line marks that row's on-screen height.
        val topRowBounds = lines.minByOrNull { it.boundingBox?.top ?: Int.MAX_VALUE }
            ?.boundingBox ?: return

        val marginRightPx = (CLOSE_BUTTON_MARGIN_RIGHT_DP * resources.displayMetrics.density).toInt()
        val tapX = (resources.displayMetrics.widthPixels - marginRightPx).toFloat()
        val tapY = (topRowBounds.centerY() + cropTopOffsetPx).toFloat()

        Log.d(TAG, "Valor/km ($valuePerKm) abaixo do limite de recusa, recusando automaticamente em ($tapX, $tapY)")
        tapAt(tapX, tapY)
    }

    private fun tapAt(x: Float, y: Float) {
        val now = System.currentTimeMillis()
        if (now - lastAutoRejectAt < AUTO_REJECT_THROTTLE_MS) return
        lastAutoRejectAt = now

        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, TAP_DURATION_MS))
            .build()
        val dispatched = dispatchGesture(gesture, null, null)
        Log.d(TAG, "auto-reject: gesto disparado=$dispatched")
    }

    private fun collectTexts(node: AccessibilityNodeInfo, out: MutableList<String>) {
        node.text?.toString()?.let { if (it.isNotBlank()) out.add(it) }
        node.contentDescription?.toString()?.let { if (it.isNotBlank()) out.add(it) }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectTexts(it, out) }
        }
    }

    override fun onInterrupt() {}

    companion object {
        private const val TAG = "RideAccessibility"
        private const val TARGET_PACKAGE = "com.ubercab.driver"
        private const val AUTO_REJECT_THROTTLE_MS = 1500L
        private const val TAP_DURATION_MS = 50L
        private const val CLOSE_BUTTON_MARGIN_RIGHT_DP = 32
        private val OFFER_BUTTON_TEXTS = listOf("Selecionar", "Aceitar")
        private val PRICE_REGEX = Regex("""R\$\s*(\d+,\d{2})""")
        private val DISTANCE_REGEX = Regex("""\((\d+\.\d+)\s*km\)""")
        private val VALUE_PER_KM_REGEX = Regex("""R\$\s*(\d+,\d{2})\s*/\s*km""")
    }
}
