---
tags: [feature, autodriver]
---

# Settings Screen

Back to [[Home]] · Related: [[Value Per KM Overlay]], [[Permissions]], [[Config Reference]]

**File:** [SettingsActivity.kt](../../app/src/main/kotlin/com/example/autodriver/SettingsActivity.kt) · Layout: [activity_settings.xml](../../app/src/main/res/layout/activity_settings.xml)

Reached from `MainActivity`'s overflow menu ("Configurações"). Lets the user tune the overlay's visible duration, test it manually, and check the accessibility permission status.

## Overlay visible time

- A `Slider` (`visible_seconds_slider`) bound to `Config.overlayVisibleSeconds`.
- Label `visible_seconds_label` shows **"Tempo visível: {n}s"**, updated live as the slider moves.
- Every change is persisted immediately via `PreferencesManager.saveConfig`.

## Test overlay button

`test_overlay_button` starts [[Value Per KM Overlay|OverlayCardService]] with hardcoded sample data (`R$ 10.96` total, `3.2 + 2.6 km`) and the current accept/reject thresholds and slider value — lets the user preview the card's appearance and timing without a live ride offer. Requires the [[Permissions|overlay permission]]; shows a Toast (*"Conceda a permissão de sobreposição primeiro"*) and aborts if it isn't granted yet.

## Accessibility permission status

Duplicates the same status check/display pattern as `MainActivity` (see [[Permissions]]): reads `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`, shows **"Concedida"/"Não concedida"**, and offers a button to jump to system Accessibility settings when not granted.
