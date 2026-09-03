---
tags: [feature, autodriver]
---

# Value Per KM Overlay

Back to [[Home]] · Related: [[Ride Offer Detection]], [[Accept-Reject Thresholds]], [[Permissions]]

**File:** [OverlayCardService.kt](../../app/src/main/kotlin/com/example/autodriver/OverlayCardService.kt) · Layout: [overlay_value_card.xml](../../app/src/main/res/layout/overlay_value_card.xml)

A `Service` (not bound, `START_NOT_STICKY`) that draws a floating card over the Uber Driver app showing the ride's value per km, so the driver doesn't have to open the offer to evaluate it.

## Lifecycle

1. Started by [[Ride Offer Detection]] with `EXTRA_TOTAL_VALUE`, `EXTRA_TOTAL_KM`, `EXTRA_ACCEPT_THRESHOLD`, `EXTRA_REJECT_THRESHOLD`, `EXTRA_VISIBLE_SECONDS`.
2. `showCard(...)` is a no-op if a card is already showing (`overlayView != null`) — only one card at a time.
3. Inflates `overlay_value_card.xml` using a themed context (`R.style.OverlayTheme`).
4. Computes `valuePerKm = totalValue / totalKm` (guarded against division by zero) and sets:
   - `overlay_value_per_km` → formatted as `"R$ %.2f/km"` (pt-BR locale).
   - `overlay_breakdown` → formatted as `"R$ %.2f ÷ %.1f km"` (pt-BR locale).
5. Applies background color based on [[Accept-Reject Thresholds]] (see below).
6. Adds the view to the `WindowManager` as a system overlay (`TYPE_APPLICATION_OVERLAY` on API 26+, `TYPE_PHONE` below), centered, offset upward by ~1.5 cm (`params.y`), non-focusable, translucent.
7. Schedules auto-dismiss via `Handler.postDelayed` after `visibleSeconds * 1000` ms, or immediately on tapping the close button (`overlay_close_button`).
8. `onDestroy` cancels the pending auto-close callback and removes the view from the `WindowManager` if still attached — this also runs when [[Ride Offer Detection]] force-stops the service because the offer disappeared.

## Requires

- [[Permissions|Overlay permission]] (`SYSTEM_ALERT_WINDOW` / `Settings.canDrawOverlays`) — the service assumes it's already granted; it does not check or request it itself.

## Manual testing

[[Settings Screen]] has a "test overlay" button that starts this service with hardcoded sample values (`R$ 10.96` over `3.2 + 2.6 km`), independent of any real offer — useful for visually checking color thresholds and overlay duration without waiting for a real ride offer.
