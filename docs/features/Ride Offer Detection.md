---
tags: [feature, autodriver]
---

# Ride Offer Detection

Back to [[Home]] · Related: [[Architecture]], [[Permissions]], [[Value Per KM Overlay]], [[Auto-Reject Low Offers]]

**File:** [RideAccessibilityService.kt](../../app/src/main/kotlin/com/example/autodriver/RideAccessibilityService.kt)

Detects when the Uber Driver app (`com.ubercab.driver`) is showing a ride offer, extracts its value/km and destination, and triggers the [[Value Per KM Overlay]].

## How it's triggered

The service only reacts to `AccessibilityEvent`s whose `packageName` equals `com.ubercab.driver`; everything else is ignored. This requires the [[Permissions|accessibility permission]] to be granted.

## Detecting that an offer is showing

The offer card (price, the "R$X,XX/km aprox." line, distance breakdown, close button) is rendered without accessible text/content-description nodes at all — only the "Selecionar"/"Aceitar" button label is exposed to the accessibility tree, and even that isn't read directly. Instead:

1. `onAccessibilityEvent` treats every event from `com.ubercab.driver` (any event type) as a cue to run OCR — see below — rather than reading anything from the tree.
2. `onOcrResult` checks the OCR'd lines against `OFFER_BUTTON_MARKERS` (substrings like `"ecionar"`, `"eitar"`, `"elect"`, `"ccept"` — robust to common single-character misreads and to the Driver app's language switching between Portuguese and English).
3. If no offer button text is found for `OFFER_MISS_TOLERANCE` consecutive passes while an offer was active, the service stops `OverlayCardService` (the offer went away) and records the trip's outcome — see [[Auto-Reject Low Offers]].

## OCR pass (price, distance, value/km, destination)

1. `maybeRequestOcr` guards it: requires API 30+ (`Build.VERSION_CODES.R`, for `takeScreenshot`), skips if an OCR request is already in flight, and throttles to at most one screenshot every 700 ms.
2. `requestScreenshot` calls `takeScreenshot(Display.DEFAULT_DISPLAY, ...)`, wraps the resulting hardware buffer into a `Bitmap`, and crops it to the region below `CROP_TOP_FRACTION` of the screen height (taller cards push their top edge higher, so this starts above a plain bottom-half crop) — the crop's top offset (`cropTopOffsetPx`) is kept for later coordinate math.
3. The cropped bitmap is run through ML Kit's `TextRecognition` (`TextRecognizerOptions.DEFAULT_OPTIONS`, Latin script).
4. Recognized lines feed `extractValuePerKm` (matches `R$X,XX/km` with an "est."/"aprox." suffix when available — see [[Auto-Reject Low Offers]] for why the suffix is preferred), `extractDestination`, `maybeAutoReject`/`maybeAutoAccept`, and `handleOfferTexts` (starts/updates the overlay).
5. `SecurityException` (missing screenshot permission) and recognizer failures are caught and logged; `ocrInFlight` is always reset so the throttle doesn't get stuck.

## Starting the overlay

`handleOfferTexts` reuses the same `extractValuePerKm` result the reject/accept decisions already computed, rather than separately re-deriving a value from the total price and distance lines — an earlier version did that with its own price/distance regexes, which broke silently whenever Uber renders a round total with no cents (`"R$ 33"` has no decimal separator, so a regex requiring one drops the offer and the card never shows). On a successful match it starts `OverlayCardService` with:

- `EXTRA_VALUE_PER_KM` — the same value/km used for the reject/accept decision.
- `EXTRA_ACCEPT_THRESHOLD`, `EXTRA_REJECT_THRESHOLD` — current values from [[Config Reference|Config]].
- `EXTRA_VISIBLE_SECONDS` — current `Config.overlayVisibleSeconds`.
- `EXTRA_DESTINATION` — `currentOfferDestination`, the neighborhood/city text from `extractDestination` (see above).

See [[Value Per KM Overlay]] for what happens next.

## Destination extraction

`extractDestination` picks the *last* line matching `ADDRESS_REGEX` (offers list pickup before drop-off, and the drop-off is what matters for [[Location Management|reject-list matching]]), then keeps only what follows the first comma — the street name itself is discarded, "number, neighborhood, city" is what's useful at a glance. `ADDRESS_REGEX` requires a whitespace character (not a word-boundary `\b`) right after the street-type prefix (`Rua`, `R.`, `Av.`, `Avenida`, `Alameda`, `Al.`, `Rodovia`, `Rod.`, `Estrada`, `Travessa`): a `\b` there silently fails to match on every *abbreviated* prefix ("R.", "Av.", "Al.", "Rod.") followed by a space, since neither the `.` nor the space is a word character and `\b` needs a word/non-word transition. Confirmed live — offers whose pickup/drop-off used "R. Pedro Osaki, Colombo" or "Av. Marginal ..., Colombo" both fell through to `UNKNOWN_DESTINATION` ("Destino desconhecido"), silently disabling reject-list matching for that offer even though "Colombo" was on the list.

## Known limitations

- `VALUE_PER_KM_REGEX`/`VALUE_PER_KM_WITH_SUFFIX_REGEX` assume Brazilian formatting (`R$ 1,82/km`, comma decimal) and require exactly two decimal digits — a per-km value Uber ever rendered without cents would hit the same silent-drop failure mode described above, just not yet observed in practice for that specific value.
