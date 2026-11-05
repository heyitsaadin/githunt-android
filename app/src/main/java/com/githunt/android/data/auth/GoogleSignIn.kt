package com.githunt.android.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

sealed interface GoogleResult {
    data class Token(val idToken: String) : GoogleResult
    data object Cancelled : GoogleResult
    data class Failed(val message: String) : GoogleResult
}

/**
 * Native "Sign in with Google".
 *
 * Android's Credential Manager shows the system account picker and hands back a
 * Google ID token (a signed proof of who the person is). We send that token to
 * our own backend (/api/auth/google/mobile), which verifies it with Google and
 * answers with a normal GitHunt session. No browser, no WebView, no password.
 *
 * [webClientId] is the *web* OAuth client id from Google Cloud, the same one the
 * website uses. Google only issues tokens to this app when an Android OAuth
 * client with this app's package name and signing SHA-1 exists in that project.
 */
class GoogleSignIn(private val webClientId: String) {

    val isConfigured: Boolean get() = webClientId.isNotBlank()

    suspend fun requestIdToken(activityContext: Context): GoogleResult {
        if (!isConfigured) return GoogleResult.Failed("Google sign-in isn't set up in this build yet.")

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId).build())
            .build()

        return try {
            val response = CredentialManager.create(activityContext)
                .getCredential(context = activityContext, request = request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                GoogleResult.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                GoogleResult.Failed("Google returned something unexpected.")
            }
        } catch (e: GetCredentialCancellationException) {
            GoogleResult.Cancelled
        } catch (e: NoCredentialException) {
            GoogleResult.Failed(
                "Google didn't offer an account. Check that this build's package name and signing key are registered in Google Cloud."
            )
        } catch (e: GetCredentialException) {
            GoogleResult.Failed(e.message ?: "Google sign-in failed.")
        } catch (e: GoogleIdTokenParsingException) {
            GoogleResult.Failed("Couldn't read Google's response.")
        }
    }
}
