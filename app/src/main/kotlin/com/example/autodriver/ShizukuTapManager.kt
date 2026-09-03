package com.example.autodriver

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import rikka.shizuku.Shizuku

/** Routes offer-card taps through a Shizuku user service instead of
 * [android.accessibilityservice.AccessibilityService.dispatchGesture] —
 * confirmed live that Uber Driver's offer card silently ignores
 * dispatchGesture touches while an identical `adb shell input tap` at the
 * same coordinates closes it instantly. Shizuku grants a normal app that
 * same shell-level tap ability, using the ADB/wireless-debugging privilege
 * the user already grants it once, without rooting the device. */
object ShizukuTapManager {

    private const val TAG = "ShizukuTapManager"
    private const val PERMISSION_REQUEST_CODE = 7341

    private var service: IShizukuTapService? = null
    var onStateChanged: (() -> Unit)? = null

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(BuildConfig.APPLICATION_ID, ShizukuTapService::class.java.name))
            .daemon(false)
            .processNameSuffix("tap")
            .debuggable(false)
            .version(1)
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = if (binder?.pingBinder() == true) IShizukuTapService.Stub.asInterface(binder) else null
            onStateChanged?.invoke()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            onStateChanged?.invoke()
        }
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == PERMISSION_REQUEST_CODE) {
            onStateChanged?.invoke()
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                bindService()
            }
        }
    }

    private var listenersRegistered = false

    fun ensureListeners() {
        if (listenersRegistered) return
        listenersRegistered = true
        try {
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
            Shizuku.addBinderReceivedListenerSticky { onStateChanged?.invoke() }
            Shizuku.addBinderDeadListener { onStateChanged?.invoke() }
        } catch (e: Throwable) {
            Log.d(TAG, "Shizuku listeners not available: ${e.message}")
        }
    }

    fun isAvailable(): Boolean =
        try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            false
        }

    fun hasPermission(): Boolean =
        try {
            isAvailable() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            false
        }

    fun requestPermission() {
        if (!isAvailable()) return
        try {
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
        } catch (e: Throwable) {
            Log.d(TAG, "requestPermission failed: ${e.message}")
        }
    }

    fun bindService() {
        if (!hasPermission() || service != null) return
        try {
            Shizuku.bindUserService(userServiceArgs, serviceConnection)
        } catch (e: Throwable) {
            Log.d(TAG, "bindUserService failed: ${e.message}")
        }
    }

    fun isServiceBound(): Boolean = service != null

    /** Returns true if the tap was actually issued through Shizuku. Callers
     * should fall back to another method (e.g. dispatchGesture) when this
     * returns false — Shizuku might not be installed, paired, or granted. */
    fun tap(x: Float, y: Float): Boolean {
        val current = service ?: return false
        return try {
            current.tap(x.toInt(), y.toInt())
            true
        } catch (e: Exception) {
            Log.d(TAG, "tap via Shizuku failed: ${e.message}")
            service = null
            false
        }
    }
}
