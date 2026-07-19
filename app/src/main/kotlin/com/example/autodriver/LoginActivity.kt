package com.example.autodriver

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        findViewById<Button>(R.id.sign_in_button).setOnClickListener {
            signIn()
        }
    }

    private fun signIn() {
        val credentialManager = CredentialManager.create(this)

        val signInOption = GetSignInWithGoogleOption.Builder(getString(R.string.google_web_client_id))
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(this@LoginActivity, request)
                val credential = GoogleIdTokenCredential.createFrom(result.credential.data)
                SessionManager.signIn(this@LoginActivity, credential.id, credential.givenName ?: credential.displayName)
                startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                finish()
            } catch (e: GetCredentialException) {
                findViewById<TextView>(R.id.login_error).text = "Falha ao autenticar: ${e.message}"
            }
        }
    }
}
