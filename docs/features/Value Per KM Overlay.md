---
tags: [feature, autodriver]
---

# Value Per KM Overlay

Back to [[Home]] · Related: [[Ride Offer Detection]], [[Accept-Reject Thresholds]], [[Permissions]]

**File:** [OverlayCardService.kt](../../app/src/main/kotlin/com/example/autodriver/OverlayCardService.kt) · Layout: [overlay_value_card.xml](../../app/src/main/res/layout/overlay_value_card.xml)

A `Service` (not bound, `START_NOT_STICKY`) that draws a floating card over the Uber Driver app showing the ride's value per km, so the driver doesn't have to open the offer to evaluate it.

## Lifecycle

1. Started by [[Ride Offer Detection]] with `EXTRA_VALUE_PER_KM`, `EXTRA_ACCEPT_THRESHOLD`, `EXTRA_REJECT_THRESHOLD`, `EXTRA_VISIBLE_SECONDS`, `EXTRA_DESTINATION`, `EXTRA_DESTINATION_BLOCKED` — at most once per offer. `RideAccessibilityService.handleOfferTexts` tracks this with `valueCardShownForOffer`, reset only when a new offer starts: without it, the moment `visibleSeconds` elapses and the card auto-closes, the next OCR pass (throttled to ~700ms) would see the same still-on-screen offer and reopen it, cycling for as long as the offer stays up instead of showing once and waiting for the next one.
2. `showCard(...)` is also a no-op if a card is already showing (`overlayView != null`) — only one card at a time.
3. Inflates `overlay_value_card.xml` using a themed context (`R.style.OverlayTheme`).
4. Sets:
   - `overlay_value_per_km` → `EXTRA_VALUE_PER_KM` formatted as `"%.2f"` (pt-BR locale, comma decimal) — the "R$/KM" unit is a separate static label next to it. The service receives this already computed rather than deriving it itself (see [[Ride Offer Detection]] for why: an earlier version recomputed it from a separately-parsed total price and distance, which silently failed whenever Uber rendered a round total with no cents).
   - `overlay_destination` → the neighborhood/city text passed in via `EXTRA_DESTINATION` (see [[Ride Offer Detection]]'s `extractDestination`). There's no value/km arithmetic shown on the card — the driver sees the rate and where the ride ends, not any underlying calculation.
   - `overlay_reject_warning` → visible (red, above the value/km row) only when `EXTRA_DESTINATION_BLOCKED` is true, i.e. `Config.isDestinationBlocked` matched the offer's destination against the [[Location Management|reject list]] (case-insensitive). This is independent of the value/km coloring below — a destination on the reject list is flagged even when the rate itself would otherwise look acceptable.
5. Colors `overlay_value_per_km`'s text and the card's stroke (an edge-lit outline, not a solid fill) based on [[Accept-Reject Thresholds]] — amber when neither threshold is met, green at/above the accept threshold, red at/below the reject threshold.
6. Adds the view to the `WindowManager` as a system overlay (`TYPE_APPLICATION_OVERLAY` on API 26+, `TYPE_PHONE` below), centered, offset upward by ~1.5 cm (`params.y`), non-focusable, translucent.
7. Schedules auto-dismiss via `Handler.postDelayed` after `visibleSeconds * 1000` ms, or immediately on tapping the close button (`overlay_close_button`).
8. `onDestroy` cancels the pending auto-close callback and removes the view from the `WindowManager` if still attached — this also runs when [[Ride Offer Detection]] force-stops the service because the offer disappeared.

## Requires

- [[Permissions|Overlay permission]] (`SYSTEM_ALERT_WINDOW` / `Settings.canDrawOverlays`) — the service assumes it's already granted; it does not check or request it itself.

## Manual testing

[[Settings Screen]] has a "test overlay" button that starts this service with a hardcoded sample value/km (`R$ 10.96` over `3.2 + 2.6 km`) and destination ("Bairro Exemplo, Cidade Exemplo"), independent of any real offer — useful for visually checking color thresholds and overlay duration without waiting for a real ride offer. The reject warning is computed the same way a real offer's would be (`Config.isDestinationBlocked(sampleDestination)`), so it only shows if that exact sample text happens to match an entry on the reject list.
