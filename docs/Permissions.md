---
tags: [reference, autodriver, permissions]
---

# Permissions

Back to [[Home]] · Related: [[Architecture]], [[Ride Offer Detection]]

Autodriver depends on two runtime permissions that Android does not grant through the normal in-app permission dialog — both must be enabled manually by the user in system settings, and `MainActivity`/`SettingsActivity` surface their current status.

## Overlay permission (`SYSTEM_ALERT_WINDOW`)

- Declared in [AndroidManifest.xml](../app/src/main/AndroidManifest.xml) as `android.permission.SYSTEM_ALERT_WINDOW`.
- Required by [[Value Per KM Overlay]] (`OverlayCardService`) to draw the floating card over the Uber Driver app.
- Checked via `Settings.canDrawOverlays(context)`.
- Granted through `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` (button in `MainActivity`).
- Status shown as **"Concedida"** (granted) / **"Não concedida"** (not granted) in green/red text; the grant button hides itself once granted.

## Accessibility service permission

- `RideAccessibilityService` is declared with `android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"` and configured via [accessibility_service_config.xml](../app/src/main/res/xml/accessibility_service_config.xml).
- Required for [[Ride Offer Detection]] to receive `AccessibilityEvent`s from `com.ubercab.driver` and to call `takeScreenshot` for the OCR fallback.
- The user enables it manually from system Accessibility settings (`Settings.ACTION_ACCESSIBILITY_SETTINGS`), since it can't be requested with a runtime permission dialog.
- Both `MainActivity` and `SettingsActivity` check it the same way: read `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` and look for `"$packageName/${RideAccessibilityService::class.java.name}"`.
- Same **"Concedida"/"Não concedida"** status pattern as the overlay permission.

## Other manifest permission

- `android.permission.INTERNET` — used by the Google Sign-In flow ([[Google Sign-In (Bypassed)]]) and ML Kit's text recognizer (model download/updates).

## Security note

Because the accessibility service reads arbitrary on-screen text (and can take screenshots) from the target app, treat every extracted string as untrusted input — see the parsing safeguards described in [[Ride Offer Detection]] and the `Error handling` section of [CLAUDE.md](../CLAUDE.md).
