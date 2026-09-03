---
tags: [feature, autodriver]
---

# Ride Offer Detection

Back to [[Home]] · Related: [[Architecture]], [[Permissions]], [[Value Per KM Overlay]]

**File:** [RideAccessibilityService.kt](../../app/src/main/kotlin/com/example/autodriver/RideAccessibilityService.kt)

Detects when the Uber Driver app (`com.ubercab.driver`) is showing a ride offer, extracts its total price and distance, and triggers the [[Value Per KM Overlay]].

## How it's triggered

The service only reacts to `AccessibilityEvent`s whose `packageName` equals `com.ubercab.driver`; everything else is ignored. This requires the [[Permissions|accessibility permission]] to be granted.

## Primary path: accessibility tree

1. `onAccessibilityEvent` collects visible text from all windows' root nodes (`windows.forEach { ... }`), falling back to `rootInActiveWindow` if nothing was found.
2. `collectTexts` recursively walks the `AccessibilityNodeInfo` tree, gathering both `node.text` and `node.contentDescription` for every node.
3. `handleOfferTexts` checks whether any collected text matches an offer button label — `"Selecionar"` or `"Aceitar"` (case-insensitive, trimmed) — via `OFFER_BUTTON_TEXTS`.
4. If no offer button is present and a card was previously shown, the service stops `OverlayCardService` (the offer went away) and returns.
5. If an offer button is present:
   - **Price**: the first text (excluding any containing `/km`) matching `PRICE_REGEX` = `R\$\s*(\d+,\d{2})`, comma converted to a dot and parsed as `Float`.
   - **Distance**: sums every match of `DISTANCE_REGEX` = `\((\d+\.\d+)\s*km\)` across all collected texts (offers can list multiple stops/legs).
   - If either price or a positive total distance isn't found, the offer is ignored (returns `false`), which lets the OCR fallback below run.

## OCR fallback

Some offer screens don't expose price/distance as accessible text — only as rendered pixels. When the accessibility tree pass doesn't produce a match:

1. `maybeRequestOcr` guards the fallback: requires API 30+ (`Build.VERSION_CODES.R`, for `takeScreenshot`), skips if an OCR request is already in flight, and throttles to at most one screenshot every 700 ms.
2. `requestScreenshot` calls `takeScreenshot(Display.DEFAULT_DISPLAY, ...)`, wraps the resulting hardware buffer into a `Bitmap`, and crops it to the **bottom half of the screen** (where the offer card appears).
3. The cropped bitmap is run through ML Kit's `TextRecognition` (`TextRecognizerOptions.DEFAULT_OPTIONS`, Latin script).
4. Recognized lines are passed back into `handleOfferTexts`, exactly like the accessibility-tree path.
5. `SecurityException` (missing screenshot permission) and recognizer failures are caught and logged; `ocrInFlight` is always reset so the throttle doesn't get stuck.

## Starting the overlay

On a successful match, `handleOfferTexts` starts `OverlayCardService` with:

- `EXTRA_TOTAL_VALUE`, `EXTRA_TOTAL_KM` — parsed offer data.
- `EXTRA_ACCEPT_THRESHOLD`, `EXTRA_REJECT_THRESHOLD` — current values from [[Config Reference|Config]].
- `EXTRA_VISIBLE_SECONDS` — current `Config.overlayVisibleSeconds`.

See [[Value Per KM Overlay]] for what happens next.

## Known limitations

- Price/distance regexes assume Brazilian formatting (`R$ 12,34`, distance in `km` with a dot decimal separator) — not internationalized.
- `acceptEnabled`/`rejectEnabled` flags from `Config` are read by `OverlayCardService` implicitly through the threshold values only; the boolean flags themselves aren't checked before applying color logic (see the caveat in [[Config Reference]]).
