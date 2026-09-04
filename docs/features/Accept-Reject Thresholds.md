---
tags: [feature, autodriver]
---

# Accept-Reject Thresholds

Back to [[Home]] · Related: [[Value Per KM Overlay]], [[Config Reference]]

**File:** [MainActivity.kt](../../app/src/main/kotlin/com/example/autodriver/MainActivity.kt) · Layout: [activity_main.xml](../../app/src/main/res/layout/activity_main.xml)

Lets the user define value-per-km thresholds so the [[Value Per KM Overlay]] can color-code offers at a glance.

## UI

On the main screen:

- **Accept switch** (`accept_enabled_switch`) + **accept value** field (`accept_when_value`) → `Config.acceptEnabled`, `Config.acceptWhenValueGreaterThan`.
- **Reject switch** (`reject_enabled_switch`) + **reject value** field (`reject_when_value`) → `Config.rejectEnabled`, `Config.rejectWhenValueLessOrEqualTo`.

Switch toggles save immediately (`PreferencesManager.saveConfig`). Threshold text fields save on every keystroke via a shared `TextWatcher` (`thresholdWatcher` → `saveThresholdsIfValid`).

## Validation

`saveThresholdsIfValid()`:

1. Parses both fields as `Float` (defaulting to `0f` if blank/invalid).
2. Rejects the update — showing the Toast *"O valor de recusar não pode ser maior ou igual ao valor de aceitar"* — if `rejectValue >= acceptValue`. In that case `Config` is left unchanged and nothing is persisted.
3. Otherwise updates `Config.acceptWhenValueGreaterThan` / `Config.rejectWhenValueLessOrEqualTo` and persists via `PreferencesManager.saveConfig`.

## How thresholds affect the overlay

`OverlayCardService.showCard` compares the offer's `valuePerKm` against the thresholds passed in:

- `valuePerKm <= rejectThreshold` → card background becomes `R.color.tw_red_600` (reject color).
- `valuePerKm >= acceptThreshold` → card background becomes `R.color.tw_green_600` (accept color).
- Otherwise the card keeps its default (neutral) background.

Note: `acceptEnabled` is stored in `Config` and editable from the UI, but is not currently read by `OverlayCardService` — only the accept threshold value is applied to coloring. `rejectEnabled` also gates [[Auto-Reject Low Offers]]'s decision logic, but that feature is currently alert-only (logs the decision, takes no action) — see its status note for why. See [[Config Reference]] for the full breakdown.
