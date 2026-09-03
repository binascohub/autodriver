package com.example.autodriver

import android.graphics.Bitmap
import android.graphics.PointF

/** Locates a small UI element (the offer card's close button) inside a
 * larger screenshot by brute-force template matching, since its on-screen
 * position isn't fixed but its appearance is. Both images are converted to
 * grayscale and downsampled before the search — matching at full resolution
 * is far too slow to run on every rejected offer. */
object ButtonImageMatcher {

    /** Returns the matched button's center in [screenshot]'s own coordinate
     * space, or null if nothing scores above [MIN_CONFIDENCE]. Only the
     * right portion of the image (from [searchLeftFraction] onward) is
     * searched, since the close button always sits in the card's top-right
     * area — this both speeds up the search and avoids false positives
     * elsewhere on screen. */
    fun findCloseButton(
        screenshot: Bitmap,
        template: Bitmap,
        searchLeftFraction: Float = 0.5f
    ): PointF? {
        val searchLeftPx = (screenshot.width * searchLeftFraction).toInt()
        val searchWidth = screenshot.width - searchLeftPx
        if (searchWidth <= 0 || screenshot.height <= 0) return null

        val haystack = downscaleGray(screenshot, searchLeftPx, 0, searchWidth, screenshot.height)
        val needle = downscaleGray(template, 0, 0, template.width, template.height)
        if (needle.width == 0 || needle.height == 0) return null
        if (haystack.width < needle.width || haystack.height < needle.height) return null

        var bestScore = Float.MAX_VALUE
        var bestX = -1
        var bestY = -1
        val maxY = haystack.height - needle.height
        val maxX = haystack.width - needle.width
        for (y in 0..maxY) {
            for (x in 0..maxX) {
                val score = sumAbsDiff(haystack, needle, x, y)
                if (score < bestScore) {
                    bestScore = score
                    bestX = x
                    bestY = y
                }
            }
        }
        if (bestX < 0) return null

        val worstPossibleScore = 255f * needle.width * needle.height
        val confidence = 1f - (bestScore / worstPossibleScore)
        if (confidence < MIN_CONFIDENCE) return null

        val centerXInHaystack = bestX + needle.width / 2f
        val centerYInHaystack = bestY + needle.height / 2f
        return PointF(
            searchLeftPx + centerXInHaystack * DOWNSAMPLE_FACTOR,
            centerYInHaystack * DOWNSAMPLE_FACTOR
        )
    }

    private class GrayImage(val width: Int, val height: Int, val pixels: IntArray)

    private fun downscaleGray(bitmap: Bitmap, left: Int, top: Int, width: Int, height: Int): GrayImage {
        val fullPixels = IntArray(width * height)
        bitmap.getPixels(fullPixels, 0, width, left, top, width, height)

        val outWidth = width / DOWNSAMPLE_FACTOR
        val outHeight = height / DOWNSAMPLE_FACTOR
        val pixels = IntArray(outWidth * outHeight)
        for (oy in 0 until outHeight) {
            for (ox in 0 until outWidth) {
                val pixel = fullPixels[(oy * DOWNSAMPLE_FACTOR) * width + (ox * DOWNSAMPLE_FACTOR)]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                pixels[oy * outWidth + ox] = (r + g + b) / 3
            }
        }
        return GrayImage(outWidth, outHeight, pixels)
    }

    private fun sumAbsDiff(haystack: GrayImage, needle: GrayImage, offsetX: Int, offsetY: Int): Float {
        var sum = 0
        for (ty in 0 until needle.height) {
            val haystackRow = (offsetY + ty) * haystack.width
            val needleRow = ty * needle.width
            for (tx in 0 until needle.width) {
                val diff = haystack.pixels[haystackRow + offsetX + tx] - needle.pixels[needleRow + tx]
                sum += if (diff < 0) -diff else diff
            }
        }
        return sum.toFloat()
    }

    private const val DOWNSAMPLE_FACTOR = 3
    private const val MIN_CONFIDENCE = 0.85f
}
