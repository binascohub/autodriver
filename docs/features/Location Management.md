---
tags: [feature, autodriver]
---

# Location Management

Back to [[Home]] · Related: [[Config Reference]], [[Auto-Reject Low Offers]]

**File:** [MainActivity.kt](../../app/src/main/kotlin/com/example/autodriver/MainActivity.kt) · Layouts: [activity_main.xml](../../app/src/main/res/layout/activity_main.xml), [item_location.xml](../../app/src/main/res/layout/item_location.xml)

A simple list of free-text addresses/locations the user can maintain from the main screen (in the "Recusar" card), backed by `Config.locations`. Any offer whose OCR-read destination matches one of these entries is auto-rejected — see [[Auto-Reject Low Offers]].

## Behavior

- **Add**: typing an address into `local_text` and tapping `add_local_button` appends the trimmed, non-empty string to `Config.locations`, adds a row to `local_list_container` (inflated from `item_location.xml`, prefixed with `"• "`), clears the input field, and persists via `PreferencesManager.saveConfig`.
- **Remove**: each row has a `remove_location_button`. Tapping it looks up the row's index within `local_list_container` and removes the matching entry from `Config.locations` (by index), then removes the row view. Note: this removal is **not** persisted immediately — it only takes effect on the next save trigger (e.g. `onPause`, or editing a threshold), since no `saveConfig` call follows the removal directly.
- **Restore on screen load**: `Config.locations.forEach { addLocationToList(it) }` repopulates the list UI from the already-loaded `Config` on `onCreate`.

## Current scope

`RideAccessibilityService.isDestinationBlocked` reads `Config.locations` on every OCR pass, matching each entry against the offer's extracted destination with a case-insensitive substring check. A match forces an auto-reject regardless of the offer's value/km — see [[Auto-Reject Low Offers]] for how this interacts with the accept threshold. The overlay itself still doesn't filter or color based on this list.
