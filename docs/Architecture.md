---
tags: [architecture, autodriver]
---

# Architecture

Back to [[Home]].

Autodriver is a single-module Android app with no ViewModel/Repository layering yet — logic lives in Activities, Services, and a couple of small singletons. There's one runtime data flow worth understanding: **offer detection → threshold evaluation → overlay rendering**.

## Component map

```
MainActivity ───────────────┐
SettingsActivity ───────────┼──► Config (in-memory) ◄──► PreferencesManager ◄──► SharedPreferences
AboutActivity                │
LoginActivity ──► SessionManager ──► SharedPreferences

RideAccessibilityService ──reads Config thresholds──► starts ──► OverlayCardService
     │
     └─ ML Kit OCR (screenshot fallback)
```

- **UI layer**: `MainActivity`, `SettingsActivity`, `AboutActivity`, `LoginActivity`. Plain `AppCompatActivity` subclasses using `findViewById`, no view binding/data binding.
- **Configuration**: [[Config Reference|Config]] is a mutable singleton (`object`) holding thresholds, saved locations, and overlay duration. [[Config Reference|PreferencesManager]] serializes/deserializes it to `SharedPreferences` under the `autodriver_config` file.
- **Session**: `SessionManager` stores a signed-in email/name in a separate `autodriver_session` preferences file. See [[Google Sign-In (Bypassed)]] — it's currently disconnected from the app's entry point.
- **Detection pipeline**: `RideAccessibilityService` is the only component that talks to the target app (`com.ubercab.driver`). See [[Ride Offer Detection]].
- **Presentation**: `OverlayCardService` is a bound-less `Service` that inflates a themed view and adds it directly to the `WindowManager` as a system overlay. See [[Value Per KM Overlay]].

## Data flow: from screen event to overlay

1. Uber Driver posts an `AccessibilityEvent` for its package.
2. `RideAccessibilityService.onAccessibilityEvent` treats this as a cue to run a screenshot + ML Kit text recognition pass (`maybeRequestOcr`, throttled to ~700ms) — see [[Ride Offer Detection]]. The offer card's button label, price, distance, and value/km are all rendered without accessible text/content-description nodes, so OCR is the only source for this data, not a fallback for tree-reading.
3. `onOcrResult` checks the recognized lines for an offer button label (`OFFER_BUTTON_MARKERS`), then runs `extractValuePerKm` and `extractDestination` on the same lines.
4. `handleOfferTexts` reuses that already-extracted value/km (rather than re-deriving it from separately-parsed price/distance text) and starts `OverlayCardService` with it, the destination, and the current thresholds/duration read from `Config`.
5. `OverlayCardService` colors the card's numeral/stroke according to [[Accept-Reject Thresholds]], adds it to the window, and auto-dismisses it after `overlayVisibleSeconds`.
6. Independently, the same OCR pass also feeds [[Auto-Reject Low Offers]]'s decision logic (value/km threshold and destination-list matching) — currently alert-only, logging the decision without acting on it (see that doc's status note).
7. If the offer button disappears for several consecutive passes, the service proactively stops `OverlayCardService` and records the trip's outcome.

## Persistence model

There are two independent `SharedPreferences` files:

| File | Owner | Contents |
|---|---|---|
| `autodriver_config` | `PreferencesManager` | Locations list, accept/reject enabled flags, thresholds, overlay visible seconds |
| `autodriver_session` | `SessionManager` | Signed-in email and display name |

`Config` is only loaded from preferences explicitly (`PreferencesManager.load`), called from `MainActivity.onCreate` and `SettingsActivity.onCreate`. It is not observable/reactive — screens read/write the singleton directly and call `saveConfig` after each mutation.

## Known architectural gaps

- No dependency injection, no ViewModel — Activities own both UI and persistence calls directly.
- `Config` being a mutable global singleton means state can drift if multiple screens are open; acceptable at the current scale but worth flagging before adding more screens.
- `LoginActivity`/`SessionManager` exist but `MainActivity` bypasses the sign-in check (`if (false && !SessionManager.isSignedIn(this))`), so the Google sign-in flow is currently dead code from the user's perspective.
- [[Auto-Reject Low Offers]] locates the close button by position heuristics on OCR output rather than an accessible node reference, since the button has no accessible node to click — this is inherently more fragile to layout changes than a normal accessibility click.
