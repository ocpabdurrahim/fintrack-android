package io.github.ocpabdurrahim.fintrack.sync

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import io.github.ocpabdurrahim.fintrack.R

/** "Sign in with Google" through Android's Credential Manager. */
object GoogleSignIn {
    class Cancelled : Exception("Sign-in cancelled")

    /** Shows Google's account picker and returns the chosen account's ID token. */
    suspend fun idToken(activity: Activity): String {
        // default_web_client_id comes from google-services.json (the project's web OAuth client).
        val option = GetSignInWithGoogleOption.Builder(activity.getString(R.string.default_web_client_id)).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val result = try {
            CredentialManager.create(activity).getCredential(activity, request)
        } catch (e: GetCredentialCancellationException) {
            throw Cancelled()
        }
        val credential = result.credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        throw IllegalStateException("Google did not return an account.")
    }
}
