package com.example.autodriver

import android.content.Context

object SessionManager {
    private const val PREFS = "autodriver_session"
    private const val KEY_EMAIL = "signed_in_email"
    private const val KEY_NAME = "signed_in_name"

    fun isSignedIn(context: Context): Boolean = signedInEmail(context) != null

    fun signedInEmail(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_EMAIL, null)

    fun signedInName(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_NAME, null)

    fun signIn(context: Context, email: String, name: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_EMAIL, email)
            .putString(KEY_NAME, name)
            .apply()
    }
}
