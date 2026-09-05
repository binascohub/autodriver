---
tags: [feature, autodriver]
---

# Ride Offer Detection

Back to [[Home]] · Related: [[Architecture]], [[Permissions]], [[Value Per KM Overlay]], [[Auto-Reject Low Offers]]

**File:** [RideAccessibilityService.kt](../../app/src/main/kotlin/com/example/autodriver/RideAccessibilityService.kt)

Detects when the Uber Driver app (`com.ubercab.driver`) is showing a ride offer, extracts its total price and distance, and triggers the [[Value Per KM Overlay]].

## How it's triggered

The service only reacts to `AccessibilityEvent`s whose `packageName` equals `com.ubercab.driver`; everything else is ignored. This requires the [[Permissions|accessibility permission]] to be granted.

## Detecting that an offer is showing

1. `onAccessibilityEvent` collects visible text from all windows' root nodes (`windows.forEach { ... }`), falling back to `rootInActiveWindow` if nothing was found.
2. `collectTexts` recursively walks the `AccessibilityNodeInfo` tree, gathering both `node.text` and `node.contentDescription` for every node.
3. It checks whether any collected text matches an offer button label — `"Selecionar"` or `"Aceitar"` (case-insensitive, trimmed) — via `OFFER_BUTTON_TEXTS`. This button label is the only part of the offer card exposed as accessible text.
4. If no offer button is present and a card was previously shown, the service stops `OverlayCardService` (the offer went away) and returns.
5. If an offer button is present, `handleOfferTexts` also tries to extract data straight from the tree text (useful when it happens to be available):
   - **Price**: the first text (excluding any containing `/km`) matching `PRICE_REGEX` = `R\$\s*(\d+,\d{2})`, comma converted to a dot and parsed as `Float`.
   - **Distance**: sums every match of `DISTANCE_REGEX` = `\((\d+\.\d+)\s*km\)` across all collected texts (offers can list multiple stops/legs).

## OCR pass (price, distance, value/km)

The offer card's price, the "R$X,XX/km aprox." line, and the distance breakdown are rendered without accessible text/content-description nodes — only the offer button label is exposed to the tree. So whenever an offer button is detected, the service always runs a screenshot + ML Kit text recognition pass on the bottom half of the screen, not just as a fallback:

1. `maybeRequestOcr` guards it: requires API 30+ (`Build.VERSION_CODES.R`, for `takeScreenshot`), skips if an OCR request is already in flight, and throttles to at most one screenshot every 700 ms.
2. `requestScreenshot` calls `takeScreenshot(Display.DEFAULT_DISPLAY, ...)`, wraps the resulting hardware buffer into a `Bitmap`, and crops it to the **bottom half of the screen** (where the offer card appears) — the crop's top offset (`cropTopOffsetPx`) is kept for later coordinate math.
3. The cropped bitmap is run through ML Kit's `TextRecognition` (`TextRecognizerOptions.DEFAULT_OPTIONS`, Latin script).
4. Recognized lines are passed into `handleOfferTexts` (same price/distance parsing as the tree path, used to show/update the overlay) **and** into `maybeAutoReject` (see [[Auto-Reject Low Offers]]), which additionally uses each line's bounding box to decide whether — and where — to tap the offer's close button.
5. `SecurityException` (missing screenshot permission) and recognizer failures are caught and logged; `ocrInFlight` is always reset so the throttle doesn't get stuck.

## Starting the overlay

`handleOfferTexts` no longer decides whether an offer button is present — that's checked once in `onAccessibilityEvent` before it's called, from either the tree pass or the OCR pass. On a successful price/distance match, it starts `OverlayCardService` with:

- `EXTRA_TOTAL_VALUE`, `EXTRA_TOTAL_KM` — parsed offer data.
- `EXTRA_ACCEPT_THRESHOLD`, `EXTRA_REJECT_THRESHOLD` — current values from [[Config Reference|Config]].
- `EXTRA_VISIBLE_SECONDS` — current `Config.overlayVisibleSeconds`.

See [[Value Per KM Overlay]] for what happens next.

## Known limitations

- Price/distance regexes assume Brazilian formatting (`R$ 12,34`, distance in `km` with a dot decimal separator) — not internationalized.
