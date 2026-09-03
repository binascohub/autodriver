# CLAUDE.md

This file guides Claude Code (and any contributor) when working in this repository. It defines coding standards expected from a senior Android/Kotlin engineer.

## Project overview

Autodriver is a Kotlin/AndroidX app (`minSdk 24`, `targetSdk 34`) that reads Uber Driver ride offers via an `AccessibilityService`, extracts price/distance with ML Kit OCR, and shows a floating overlay with the value per km. See [README.md](README.md) for functional details.

## Language policy

- **All code, identifiers, comments, commit messages, and documentation (including this file) must be written in English.**
- **Only text visible to the end user in the app UI must be in Portuguese (pt-BR)**: strings in `res/values/strings.xml`, layout copy, Toast/Snackbar/dialog messages, notification text, and any other string rendered on screen.
- Never hardcode UI text directly in Kotlin or XML layouts — always reference a string resource in `strings.xml` so the Portuguese-only rule stays enforceable in one place.
- Log messages (`Log.d`, `Log.e`, etc.), exception messages, and internal error strings are developer-facing, not user-facing — keep those in English.

## Kotlin style and conventions

- Follow the official [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html); rely on Android Studio's default formatter (ktlint-compatible: 4-space indent, no wildcard imports, trailing commas where useful).
- Prefer `val` over `var`; avoid mutable state unless required.
- Use expressive, intention-revealing names (`acceptThresholdPerKm`, not `t1`); avoid abbreviations.
- Favor Kotlin idioms: data classes, sealed classes/interfaces for state, extension functions, scope functions (`let`, `apply`, `run`) used sparingly and only when they improve readability.
- Null safety: avoid `!!`; prefer safe calls, `requireNotNull`, or early returns with clear failure handling.
- Keep functions small and single-purpose; extract private helpers instead of writing long methods with nested conditionals.
- No commented-out code, no dead code, no TODOs without an owner/issue reference.

## Architecture and structure

- Keep Activities/Services thin: UI wiring and lifecycle only. Business logic (offer parsing, threshold evaluation, session state) belongs in dedicated classes (e.g. `Config.kt`, `SessionManager.kt`, `PreferencesManager.kt`), not inline in `MainActivity`/`RideAccessibilityService`.
- One class per responsibility. If a class starts handling parsing, persistence, and UI updates at once, split it.
- Persist user preferences only through `PreferencesManager`; do not read/write `SharedPreferences` ad hoc elsewhere.
- Any new screen-visible constant, threshold, or default value goes in `Config.kt` or resources — not as a magic number inline.
- Prefer coroutines + structured concurrency (`viewModelScope`/`lifecycleScope` or an explicit scope tied to component lifecycle) over raw threads or callbacks for async work (OCR, accessibility event processing).

## Android-specific practices

- Respect lifecycle: unregister listeners, cancel coroutines, and release overlay views in `onDestroy`/`onStopped` to avoid leaks, especially in `OverlayCardService` and `RideAccessibilityService`, which run outside a normal Activity lifecycle.
- Guard all permission-dependent code paths (overlay permission, accessibility permission) with explicit checks before use; never assume a permission is granted.
- Treat `AccessibilityService` and OCR text extraction as untrusted input — validate parsed values (price, distance) before using them in calculations; never crash on unexpected screen text.
- Do not log sensitive data (ride details, account/session info) at levels that ship in release builds.
- Use `androidx.credentials` APIs as already wired for Google sign-in; don't reintroduce deprecated `GoogleSignInClient` APIs.

## Error handling

- Fail safely in background components (accessibility/overlay services): catch and log unexpected exceptions instead of letting the service crash silently.
- Validate external/OCR input at the boundary; don't propagate malformed data deep into business logic.
- Don't swallow exceptions silently — log with context (English) or surface a user-facing message (Portuguese) when appropriate.

## Dependencies and build

- Declare all dependencies with explicit versions in `build.gradle.kts`; avoid dynamic versions (`+`).
- Before adding a new dependency, check if an existing one (AndroidX, Material, ML Kit, Coroutines) already covers the need.
- Keep `minSdk`/`targetSdk` and Kotlin/Gradle versions in sync with what's documented in [README.md](README.md); update the README if you change them.

## Testing

- New business logic (offer parsing, threshold/accept-reject evaluation, session/preferences handling) should be covered by unit tests where feasible.
- Prefer testing pure logic extracted out of Activities/Services rather than trying to test Android components directly.

## Documentation

- After finishing a task that adds, changes, or removes a feature, document it in `docs/` (create the file if it doesn't exist yet).
- Documentation files themselves follow the language policy above: written in English, except for verbatim quotes of user-facing Portuguese strings when relevant.
- Keep each feature's documentation focused: what it does, how it's triggered, relevant files/classes involved, and any configuration or thresholds involved.
- Update existing docs instead of duplicating content when a change modifies a previously documented feature.
- If a change is significant enough to affect the project overview, also update [README.md](README.md) accordingly.

## Git and commits

- Write commit messages in English, in the imperative mood, describing the "why" when it's not obvious from the diff.
- Keep commits focused; don't mix unrelated refactors with feature/bugfix changes.
