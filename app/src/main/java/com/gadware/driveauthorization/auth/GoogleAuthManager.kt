package com.gadware.driveauthorization.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.gadware.driveauthorization.R
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.common.api.ApiException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.tasks.await

class GoogleAuthManager(private val context: Context) {

    private val credentialManager = CredentialManager.create(context)

    // TODO: Ideally this should be in build config or resources
    // User must create a "Web application" Client ID in Google Cloud Console even for Android apps when using CredMan
    // This is required for GetGoogleIdOption.
    // Replace this with your actual Web Client ID
    private val webClientId = "YOUR_WEB_CLIENT_ID_placeholder" 

    suspend fun signIn(activity: Activity): Result<String> {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .setCredentialOptions(listOf(googleIdOption))
            .build()

        return try {
            val result = credentialManager.getCredential(activity, request)
            when (val credential = result.credential) {
                is GoogleIdTokenCredential -> {
                    // We only need the ID (email) to proceed with Drive Auth
                    val email = credential.id
                    Result.success(email)
                }
                else -> {
                    Result.failure(Exception("Unexpected credential type: ${credential.type}"))
                }
            }
        } catch (e: GetCredentialException) {
            Log.e("GoogleAuthManager", "Sign in failed", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e("GoogleAuthManager", "Sign in error", e)
            Result.failure(e)
        }
    }

    suspend fun requestDriveAccess(activity: Activity, email: String): Result<Boolean> {
        val authorizationClient = Identity.getAuthorizationClient(activity)
        val requestedScopes = listOf(Scope(DriveScopes.DRIVE_APPDATA))
        
        val authorizationRequest = AuthorizationRequest.builder()
            .setRequestedScopes(requestedScopes)
            .setAccount(android.accounts.Account(email, "com.google"))
            .build()

        return try {
            // authorize() returns AuthorizationResult
            val result = authorizationClient.authorize(authorizationRequest).await()
            if (result.hasResolution()) {
                // Access needs UI approval (PendingIntent)
                // The caller must launch this intent
                // For simplicity here, we assume the caller handles the resolution separately 
                // However, AuthorizationClient.authorize typically launches automatically or returns a PendingIntent?
                // Wait, result.pendingIntent IS the resolution.
                // We need to launch it.
                // But this method is suspend. We can't easily launchForResults here without ActivityResultCaller.
                // WE need to return the PendingIntent to the UI/ViewModel?
                // The standard pattern is `registerForActivityResult`.
                Result.failure(AuthResolutionRequiredException(result.pendingIntent))
            } else {
                // Granted
                Result.success(true)
            }
        } catch (e: ApiException) {
             Log.e("GoogleAuthManager", "Auth failed", e)
             Result.failure(e)
        }
    }
}

class AuthResolutionRequiredException(val pendingIntent: android.app.PendingIntent?) : Exception("User resolution required")
