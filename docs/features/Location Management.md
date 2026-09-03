---
tags: [feature, autodriver]
---

# Location Management

Back to [[Home]] · Related: [[Config Reference]]

**File:** [MainActivity.kt](../../app/src/main/kotlin/com/example/autodriver/MainActivity.kt) · Layouts: [activity_main.xml](../../app/src/main/res/layout/activity_main.xml), [item_location.xml](../../app/src/main/res/layout/item_location.xml)

A simple list of free-text addresses/locations the user can maintain from the main screen, backed by `Config.locations`.

## Behavior

- **Add**: typing an address into `local_text` and tapping `add_local_button` appends the trimmed, non-empty string to `Config.locations`, adds a row to `local_list_container` (inflated from `item_location.xml`, prefixed with `"• "`), clears the input field, and persists via `PreferencesManager.saveConfig`.
- **Remove**: each row has a `remove_location_button`. Tapping it looks up the row's index within `local_list_container` and removes the matching entry from `Config.locations` (by index), then removes the row view. Note: this removal is **not** persisted immediately — it only takes effect on the next save trigger (e.g. `onPause`, or editing a threshold), since no `saveConfig` call follows the removal directly.
- **Restore on screen load**: `Config.locations.forEach { addLocationToList(it) }` repopulates the list UI from the already-loaded `Config` on `onCreate`.

## Current scope

This is presentation-only bookkeeping today — `Config.locations` is populated and persisted, but no other component in the app (offer detection, overlay) currently reads or filters based on it. It's reserved for future functionality (e.g. only showing the overlay near specific pickup areas).
