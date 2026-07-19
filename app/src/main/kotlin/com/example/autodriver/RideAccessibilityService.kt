package com.example.autodriver

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class RideAccessibilityService : AccessibilityService() {

    private var offerShowing = false
    private var lastScreenshotAt = 0L
    private var ocrInFlight = false
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName != TARGET_PACKAGE) return

        val texts = mutableListOf<String>()
        windows.forEach { window -> window.root?.let { collectTexts(it, texts) } }
        if (texts.isEmpty()) rootInActiveWindow?.let { collectTexts(it, texts) }

        if (handleOfferTexts(texts)) return

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

                val bottomHalf = Bitmap.createBitmap(
                    fullBitmap,
                    0,
                    fullBitmap.height / 2,
                    fullBitmap.width,
                    fullBitmap.height - fullBitmap.height / 2
                )
                fullBitmap.recycle()

                recognizer.process(InputImage.fromBitmap(bottomHalf, 0))
                    .addOnSuccessListener { visionText ->
                        ocrInFlight = false
                        val lines = visionText.textBlocks.flatMap { block -> block.lines.map { it.text } }
                        Log.d(TAG, "OCR lines=$lines")
                        handleOfferTexts(lines)
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
        val hasOfferButton = texts.any { text ->
            OFFER_BUTTON_TEXTS.any { text.trim().equals(it, ignoreCase = true) }
        }
        if (!hasOfferButton) {
            if (offerShowing) {
                Log.d(TAG, "botão de oferta sumiu, fechando card")
                stopService(Intent(this, OverlayCardService::class.java))
                offerShowing = false
            }
            return false
        }

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
        intent.putExtra(OverlayCardService.EXTRA_DESTINATION, extractDestination(texts))
        startService(intent)
        offerShowing = true
        return true
    }

    private fun extractDestination(texts: List<String>): String {
        return texts.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                val segments = line.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                when {
                    segments.size >= 3 && line.contains(Regex("\\b(Rua|Av|Avenida|R\\.|Trav|Praça|Alameda)\\b"), ignoreCase = true) -> {
                        segments.takeLast(2).joinToString(", ")
                    }
                    segments.size >= 2 && line.contains(Regex("\\b(São|Sao|Santa|Vila|Centro|Colombo|Gabriel)\\b"), ignoreCase = true) -> {
                        segments.takeLast(2).joinToString(", ")
                    }
                    else -> null
                }
            }
            .firstOrNull()
            ?: texts.asSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapNotNull { line ->
                    val segments = line.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    if (segments.size >= 2) segments.takeLast(2).joinToString(", ") else null
                }
                .firstOrNull()
            ?: ""
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
        private val OFFER_BUTTON_TEXTS = listOf("Selecionar", "Aceitar")
        private val PRICE_REGEX = Regex("""R\$\s*(\d+,\d{2})""")
        private val DISTANCE_REGEX = Regex("""\((\d+\.\d+)\s*km\)""")
    }
}
