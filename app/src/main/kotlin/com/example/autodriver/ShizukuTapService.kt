package com.example.autodriver

import android.util.Log

/** Instantiated by Shizuku in a separate, shell-privileged process — this
 * class never runs inside our own app process. Shelling out to `input tap`
 * from here works because that process holds the shell UID's
 * `android.permission.INJECT_EVENTS`, unlike a normal app (or an
 * AccessibilityService's dispatchGesture, which Uber Driver silently
 * ignores on its offer card). */
class ShizukuTapService : IShizukuTapService.Stub() {

    override fun tap(x: Int, y: Int) {
        try {
            Runtime.getRuntime().exec(arrayOf("input", "tap", x.toString(), y.toString())).waitFor()
        } catch (e: Exception) {
            Log.e(TAG, "shell input tap failed: ${e.message}")
        }
    }

    override fun destroy() {
        System.exit(0)
    }

    companion object {
        private const val TAG = "ShizukuTapService"
    }
}
