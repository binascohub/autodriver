---
tags: [moc, autodriver]
---

# Autodriver

Index note (MOC — Map of Content) for the Autodriver documentation vault. Open this vault folder (`docs/`) directly in Obsidian to browse it with backlinks and the graph view.

Autodriver is a Kotlin/AndroidX app that watches ride offers inside the Uber Driver app and shows a floating overlay with the value per km, so the driver can decide to accept or reject without opening the offer.

## Start here

- [[Architecture]] — components, data flow, and how the pieces fit together.
- [[Config Reference]] — the in-memory configuration model and how it's persisted.
- [[Permissions]] — Accessibility and overlay permissions the app depends on.

## Features

- [[Ride Offer Detection]] — now also covers why the OCR pass runs on every offer, not just as a fallback
- [[Value Per KM Overlay]]
- [[Accept-Reject Thresholds]]
- [[Auto-Reject Low Offers]] — new: automatically declines offers below the reject threshold
- [[Location Management]]
- [[Settings Screen]]
- [[Google Sign-In (Bypassed)]]

## Source map

| Class | File | Responsibility |
|---|---|---|
| `MainActivity` | [MainActivity.kt](../app/src/main/kotlin/com/example/autodriver/MainActivity.kt) | Main screen: locations, thresholds, permission status |
| `SettingsActivity` | [SettingsActivity.kt](../app/src/main/kotlin/com/example/autodriver/SettingsActivity.kt) | Overlay visible-time slider, overlay test, accessibility status |
| `AboutActivity` | [AboutActivity.kt](../app/src/main/kotlin/com/example/autodriver/AboutActivity.kt) | Static "about" screen |
| `LoginActivity` | [LoginActivity.kt](../app/src/main/kotlin/com/example/autodriver/LoginActivity.kt) | Google sign-in flow (currently bypassed) |
| `RideAccessibilityService` | [RideAccessibilityService.kt](../app/src/main/kotlin/com/example/autodriver/RideAccessibilityService.kt) | Reads Uber Driver screen content, detects offers, runs OCR fallback |
| `OverlayCardService` | [OverlayCardService.kt](../app/src/main/kotlin/com/example/autodriver/OverlayCardService.kt) | Renders the floating value-per-km card |
| `Config` | [Config.kt](../app/src/main/kotlin/com/example/autodriver/Config.kt) | In-memory app configuration (singleton) |
| `PreferencesManager` | [PreferencesManager.kt](../app/src/main/kotlin/com/example/autodriver/PreferencesManager.kt) | Persists/restores `Config` via `SharedPreferences` |
| `SessionManager` | [SessionManager.kt](../app/src/main/kotlin/com/example/autodriver/SessionManager.kt) | Stores signed-in user email/name |

See also the project [README](../README.md) (in Portuguese) and [CLAUDE.md](../CLAUDE.md) for coding standards.
