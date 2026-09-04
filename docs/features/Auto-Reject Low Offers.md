---
tags: [feature, autodriver]
---

# Auto-Reject Low Offers

Back to [[Home]] · Related: [[Ride Offer Detection]], [[Accept-Reject Thresholds]], [[Location Management]], [[Permissions]], [[Config Reference]]

**File:** [RideAccessibilityService.kt](../../app/src/main/kotlin/com/example/autodriver/RideAccessibilityService.kt)

## Current status: alert-only, no automatic tap

Uber Driver silently ignores `AccessibilityService.dispatchGesture()` touches on its offer card's close/accept buttons specifically — confirmed live with reproducible before/after screenshots (identical coordinates via `adb shell input tap` close the card instantly; dispatchGesture at those same coordinates does nothing at all). A Shizuku-based (non-root, shell-privileged) tap mechanism was built and removed again after hitting an unresolved upstream OEM bug ([RikkaApps/Shizuku#1198](https://github.com/RikkaApps/Shizuku/issues/1198)) that crashes Shizuku's UserService on Xiaomi/MIUI devices with a MediaTek chipset before any tap could be issued.

As a result, **`maybeAutoReject`/`maybeAutoAccept` currently only detect and log the decision — they never act on it.** `onOcrResult` always shows the [[Value Per KM Overlay|alert card]] regardless of what these functions decide, and every offer is recorded in history as `NO_ACTION` (see [[Config Reference]]) since the app never actually taps anything. The decision logic (thresholds, destination-list matching) and the tap-location code below are kept intact so a real tap mechanism can be wired back in later without re-deriving this logic.

## Decision logic (currently alert-only, see above)

`maybeAutoReject`/`maybeAutoAccept` compute (and log, throttled) whenever either of two conditions holds:

- the value/km shown by Uber on the offer card is at or below the configured reject threshold, or
- the offer's destination matches an entry in the [[Location Management|reject list]].

### Priority over auto-accept

`onOcrResult` calls both `maybeAutoReject` and `maybeAutoAccept` unconditionally (neither gates the other today, since neither takes any action). Once a real tap mechanism is wired back in, the intended priority is: a destination on the reject list should force a reject even when the value/km is high enough to otherwise qualify for auto-accept — evaluate `maybeAutoReject` first and only fall through to `maybeAutoAccept` when it returns `false`, as before.

## Why OCR, not the accessibility tree

The offer card (product name, total price, the "R$X,XX/km aprox." line, distance breakdown, close button) is rendered without accessible text/content-description nodes — only the "Selecionar"/"Aceitar" button text is exposed to the accessibility tree. This card always occupies roughly the bottom half of the screen. Because of that:

- [[Ride Offer Detection]]'s OCR fallback is no longer only a fallback for this data: `onAccessibilityEvent` now runs it (throttled to once per ~700ms) **every time an offer button is present**, not just when tree-based parsing fails, since it also supplies the pixel coordinates this feature needs.
- The auto-reject decision and tap coordinates come exclusively from the OCR pass (`maybeAutoReject`, called from the screenshot recognizer's success callback in `requestScreenshot()`), not from the accessibility tree.

## How the threshold check works

1. `maybeAutoReject(texts, cardBitmap, cropTopOffsetPx)` runs on every successful OCR pass while an offer is showing.
2. It's a no-op unless `Config.rejectEnabled` is `true`.
3. It reads the value/km directly off the screen using `VALUE_PER_KM_REGEX` = `R\$\s*(\d+,\d{2})\s*/\s*km`, matching the "R$1,53/km" text Uber already renders on the card — this is the same number the driver sees, not a recomputed value.
4. It also checks `Config.isDestinationBlocked(currentOfferDestination)` — a case-insensitive substring match against every entry in `Config.locations`.
5. The reject condition is met if *either* the value/km is at or below `Config.rejectWhenValueLessOrEqualTo` *or* the destination is blocked — this only produces a log line today (see status note above), not an action.
6. `currentOfferDestination` (and `currentOfferValuePerKm`) reset to their unknown/`null` defaults whenever a new offer starts (the `!offerActive` branch in `onOcrResult`), so a value read from the *previous* offer can never leak into this decision if the new offer's own OCR pass hasn't resolved a destination yet.
7. The log line for a destination-blocked reject never includes the destination text itself — only "destination is on the reject list" — since `Log.d` output ships in release builds and ride details must not appear there.

## How the tap location is computed (currently unused, see status note above)

There's no accessible node to click, so `closeCurrentOffer`/`acceptCurrentOffer` locate a tap point and `tapAt` simulates a touch with `dispatchGesture` (requires `android:canPerformGestures="true"`, added to [accessibility_service_config.xml](../../app/src/main/res/xml/accessibility_service_config.xml)) — this call never actually closes the card (see status note above), but is kept as the plumbing to swap a working tap mechanism into:

1. `closeCurrentOffer` first tries [ButtonImageMatcher](../../app/src/main/kotlin/com/example/autodriver/ButtonImageMatcher.kt) to locate the close ("X") button by template match against `close_button_template.png` (bundled in `app/src/main/assets/`) — the button's appearance is stable across offers even though its Y position shifts with card height (more stops/legs push the card's top edge higher).
2. If no confident match is found and a position was captured via Settings > "Capturar posição do X", it falls back to `sweepTapCloseButton`: a column of taps starting at that captured position and sweeping upward by `SWEEP_STEP_PX` every `SWEEP_STEP_DELAY_MS`, up to `SWEEP_SPAN_PX` total.
3. `acceptCurrentOffer` only ever taps the position captured via Settings > "Capturar posição do Aceitar" — there's no safe geometry-based guess for a full-width bottom button.
4. Repeated triggers are throttled to one attempt per 1.5s (`AUTO_ACTION_THROTTLE_MS`) via `lastAutoRejectAt`/`lastAutoAcceptAt`.

## Known limitations

- No tap mechanism currently reaches Uber's offer buttons — see the status note above. This is the primary limitation; everything below applies once one is wired back in.
- If OCR doesn't recognize the "/km" line on a given pass (poor contrast, screen animation mid-transition), that pass simply skips the reject/accept check — it will typically succeed on the next throttled OCR pass while the card is still showing.
- This feature depends on the same accessibility + overlay permissions described in [[Permissions]], plus the gesture-dispatch capability (unused today, but still declared).
