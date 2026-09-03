---
tags: [feature, autodriver, dead-code]
---

# Google Sign-In (Bypassed)

Back to [[Home]] · Related: [[Architecture]]

**Files:** [LoginActivity.kt](../../app/src/main/kotlin/com/example/autodriver/LoginActivity.kt), [SessionManager.kt](../../app/src/main/kotlin/com/example/autodriver/SessionManager.kt) · Layout: [activity_login.xml](../../app/src/main/res/layout/activity_login.xml)

A Google Sign-In flow built with `androidx.credentials`, currently **not reachable** from the app's normal entry point.

## Current status

`MainActivity.onCreate` guards the redirect to `LoginActivity` with a condition hardcoded to `false`:

```kotlin
if (false && !SessionManager.isSignedIn(this)) {
    startActivity(Intent(this, LoginActivity::class.java))
    finish()
    return
}
```

This means the login screen is dead code from a user-facing perspective — the app always opens directly on `MainActivity` regardless of sign-in state.

## How it would work (if re-enabled)

1. `LoginActivity` builds a `GetSignInWithGoogleOption` using `R.string.google_web_client_id` and requests a credential via `CredentialManager`.
2. On success, it extracts a `GoogleIdTokenCredential`, then calls `SessionManager.signIn(context, email = credential.id, name = credential.givenName ?: credential.displayName)`.
3. `SessionManager` persists `signed_in_email` / `signed_in_name` in the `autodriver_session` `SharedPreferences` file and is queried via `isSignedIn(context)`.
4. On failure (`GetCredentialException`), the error is shown inline in `login_error` as **"Falha ao autenticar: {message}"**.

## Setup prerequisite

`res/values/strings.xml` must have `google_web_client_id` replaced with a real OAuth Web client ID for this flow to function (see [README.md](../../README.md)).

## Before re-enabling

If this flow is reactivated, revisit:
- Whether `SessionManager`'s session state should ever expire/be invalidated (there's currently no sign-out).
- Whether email/name should be stored more securely than plain `SharedPreferences`.
