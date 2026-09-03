---
tags: [feature, autodriver]
---

# Auto-Reject Low Offers

Back to [[Home]] · Related: [[Ride Offer Detection]], [[Accept-Reject Thresholds]], [[Location Management]], [[Permissions]], [[Config Reference]]

**File:** [RideAccessibilityService.kt](../../app/src/main/kotlin/com/example/autodriver/RideAccessibilityService.kt)

Automatically declines a ride offer by tapping its close ("X") button when either of two conditions holds — no driver interaction needed:

- the value/km shown by Uber on the offer card is at or below the configured reject threshold, or
- the offer's destination matches an entry in the [[Location Management|reject list]].

## Priority over auto-accept

`onOcrResult` only evaluates `maybeAutoAccept` when `maybeAutoReject` returns `false` for that pass. Because a blocked destination makes `maybeAutoReject` return `true` on its own — independent of the value/km reading — a destination on the reject list always wins even when the offer's value/km is high enough to otherwise qualify for auto-accept. There is no configuration to invert this priority.

## Why OCR, not the accessibility tree

The offer card (product name, total price, the "R$X,XX/km aprox." line, distance breakdown, close button) is rendered without accessible text/content-description nodes — only the "Selecionar"/"Aceitar" button text is exposed to the accessibility tree. This card always occupies roughly the bottom half of the screen. Because of that:

- [[Ride Offer Detection]]'s OCR fallback is no longer only a fallback for this data: `onAccessibilityEvent` now runs it (throttled to once per ~700ms) **every time an offer button is present**, not just when tree-based parsing fails, since it also supplies the pixel coordinates this feature needs.
- The auto-reject decision and tap coordinates come exclusively from the OCR pass (`maybeAutoReject`, called from the screenshot recognizer's success callback in `requestScreenshot()`), not from the accessibility tree.

## How the threshold check works

1. `maybeAutoReject(texts, cardBitmap, cropTopOffsetPx)` runs on every successful OCR pass while an offer is showing.
2. It's a no-op unless `Config.rejectEnabled` is `true`.
3. It reads the value/km directly off the screen using `VALUE_PER_KM_REGEX` = `R\$\s*(\d+,\d{2})\s*/\s*km`, matching the "R$1,53/km" text Uber already renders on the card — this is the same number the driver sees, not a recomputed value.
4. It also checks `isDestinationBlocked(currentOfferDestination)` — a case-insensitive substring match against every entry in `Config.locations`.
5. The offer is auto-rejected if *either* the value/km is at or below `Config.rejectWhenValueLessOrEqualTo` *or* the destination is blocked. If neither holds, nothing happens (offer is left for the driver, and [[Value Per KM Overlay]] still shows/colors normally via the existing `handleOfferTexts` call).

## How the tap location is computed

There's no accessible node to click, so the service simulates a touch with `dispatchGesture` (requires `android:canPerformGestures="true"`, added to [accessibility_service_config.xml](../../app/src/main/res/xml/accessibility_service_config.xml)):

1. The product name and the close button sit on the same top row of the card. The **topmost recognized OCR line's bounding box** is used as a stand-in for that row's vertical position — this adapts automatically to cards of different heights (more stops/legs push the card's top edge higher), instead of relying on a fixed screen coordinate.
2. That line's bounding box is in the *cropped bottom-half bitmap's* coordinate space, so its `top`/`centerY()` is offset back by `cropTopOffsetPx` (half the full screenshot's height) to get real screen coordinates.
3. The horizontal tap position is fixed at `screenWidth - 32dp`, matching where the close icon sits near the right edge of the card in the observed layout.
4. A single short tap (`GestureDescription.StrokeDescription`, ~50ms) is dispatched at `(tapX, tapY)`.
5. Repeated triggers are throttled to one tap per 1.5s (`AUTO_REJECT_THROTTLE_MS`) via `lastAutoRejectAt`, so a few back-to-back accessibility/OCR events for the same offer don't fire multiple taps.

## Known limitations

- The horizontal margin (`CLOSE_BUTTON_MARGIN_RIGHT_DP`) is a fixed value tuned to the observed card layout; it will need adjusting if Uber changes the card's padding/close-button size, since there is no accessible bounding box for the icon itself to measure against.
- If OCR doesn't recognize the "/km" line on a given pass (poor contrast, screen animation mid-transition), that pass simply skips the auto-reject check — it will typically succeed on the next throttled OCR pass while the card is still showing.
- This feature depends on the same accessibility + overlay permissions described in [[Permissions]], plus the newly required gesture-dispatch capability.
