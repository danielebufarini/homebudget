package it.homebudget.app.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Base64
import androidx.activity.result.IntentSenderRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import it.homebudget.shared.R
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.SecureRandom
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val GOOGLE_DRIVE_BACKUP_PREFS = "google-drive-backup"
private const val GOOGLE_DRIVE_BACKUP_ENABLED_KEY = "enabled"

enum class DriveAuthorizationState {
    Authorized,
    NeedsUserAction,
    Unavailable
}

data class DriveAuthorizationSnapshot(
    val isDriveBackupEnabled: Boolean,
    val state: DriveAuthorizationState,
    val errorMessage: String? = null
)

sealed interface DriveAuthorizationResult {
    data object Authorized : DriveAuthorizationResult
    data class NeedsResolution(val request: IntentSenderRequest) : DriveAuthorizationResult
    data class Failed(val message: String?) : DriveAuthorizationResult
}

class GoogleDriveAuthorizationManager(
    private val context: Context
) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(GOOGLE_DRIVE_BACKUP_PREFS, Context.MODE_PRIVATE)
    private val credentialManager by lazy { CredentialManager.create(appContext) }

    fun isDriveBackupEnabled(): Boolean {
        return preferences.getBoolean(GOOGLE_DRIVE_BACKUP_ENABLED_KEY, false)
    }

    fun setDriveBackupEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean(GOOGLE_DRIVE_BACKUP_ENABLED_KEY, enabled)
            .apply()
    }

    fun hasWebClientIdConfigured(): Boolean {
        return appContext.getString(R.string.google_web_client_id).trim().isNotEmpty()
    }

    suspend fun checkDriveAuthorizationStatus(): DriveAuthorizationSnapshot {
        return if (!isDriveBackupEnabled()) {
            DriveAuthorizationSnapshot(
                isDriveBackupEnabled = false,
                state = DriveAuthorizationState.NeedsUserAction
            )
        } else {
            buildSnapshot(authorizationResult = requestDriveAuthorizationSilently())
        }
    }

    suspend fun getAuthorizedAccessTokenOrNull(): String? {
        if (!isDriveBackupEnabled()) {
            return null
        }

        val authorizationResult = requestDriveAuthorizationSilently() ?: return null
        if (authorizationResult.hasResolution()) {
            return null
        }
        return authorizationResult.accessToken
    }

    suspend fun beginManualAuthorization(activity: Activity): DriveAuthorizationResult {
        runCatching {
            attemptReturningUserGoogleSignIn(activity)
        }

        val authorizationResult = requestDriveAuthorization(activity) ?: return DriveAuthorizationResult.Failed(null)
        if (authorizationResult.hasResolution()) {
            val pendingIntent = authorizationResult.pendingIntent
                ?: return DriveAuthorizationResult.Failed(null)
            return DriveAuthorizationResult.NeedsResolution(
                IntentSenderRequest.Builder(pendingIntent).build()
            )
        }

        val accessToken = authorizationResult.accessToken
        if (accessToken.isNullOrBlank()) {
            return DriveAuthorizationResult.Failed(null)
        }

        setDriveBackupEnabled(true)
        return DriveAuthorizationResult.Authorized
    }

    suspend fun completeManualAuthorization(data: Intent?): DriveAuthorizationResult {
        if (data == null) {
            return DriveAuthorizationResult.Failed(null)
        }

        return runCatching {
            Identity.getAuthorizationClient(appContext)
                .getAuthorizationResultFromIntent(data)
        }.fold(
            onSuccess = { authorizationResult ->
                val accessToken = authorizationResult.accessToken
                if (accessToken.isNullOrBlank()) {
                    DriveAuthorizationResult.Failed(null)
                } else {
                    setDriveBackupEnabled(true)
                    DriveAuthorizationResult.Authorized
                }
            },
            onFailure = { error ->
                setDriveBackupEnabled(false)
                DriveAuthorizationResult.Failed(error.message)
            }
        )
    }

    private suspend fun buildSnapshot(authorizationResult: AuthorizationResult?): DriveAuthorizationSnapshot {
        if (authorizationResult == null) {
            return DriveAuthorizationSnapshot(
                isDriveBackupEnabled = true,
                state = DriveAuthorizationState.Unavailable
            )
        }

        if (authorizationResult.hasResolution()) {
            return DriveAuthorizationSnapshot(
                isDriveBackupEnabled = true,
                state = DriveAuthorizationState.NeedsUserAction
            )
        }

        val accessToken = authorizationResult.accessToken
        return if (accessToken.isNullOrBlank()) {
            DriveAuthorizationSnapshot(
                isDriveBackupEnabled = true,
                state = DriveAuthorizationState.Unavailable
            )
        } else {
            DriveAuthorizationSnapshot(
                isDriveBackupEnabled = true,
                state = DriveAuthorizationState.Authorized
            )
        }
    }

    private suspend fun requestDriveAuthorizationSilently(): AuthorizationResult? {
        return runCatching {
            Identity.getAuthorizationClient(appContext)
                .authorize(buildAuthorizationRequest())
                .awaitResult()
        }.getOrNull()
    }

    private suspend fun requestDriveAuthorization(activity: Activity): AuthorizationResult? {
        return runCatching {
            Identity.getAuthorizationClient(activity)
                .authorize(buildAuthorizationRequest())
                .awaitResult()
        }.getOrNull()
    }

    private fun buildAuthorizationRequest(): AuthorizationRequest {
        return AuthorizationRequest.builder()
            .setRequestedScopes(listOf(googleDriveBackupScope()))
            .build()
    }

    private suspend fun attemptReturningUserGoogleSignIn(activity: Activity) {
        val webClientId = appContext.getString(R.string.google_web_client_id).trim()
        if (webClientId.isEmpty()) {
            return
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            .setFilterByAuthorizedAccounts(true)
            .setAutoSelectEnabled(true)
            .setNonce(generateSecureRandomNonce())
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        try {
            val response = credentialManager.getCredential(
                context = activity,
                request = request
            )
            response.credential.asGoogleIdTokenCredentialOrNull()
        } catch (_: NoCredentialException) {
            // No returning Google account is available for silent sign-in.
        } catch (_: GetCredentialException) {
            // Ignore: Drive authorization will fall back to GIS authorization.
        }
    }

    private fun generateSecureRandomNonce(byteLength: Int = 32): String {
        val randomBytes = ByteArray(byteLength)
        SecureRandom().nextBytes(randomBytes)
        return Base64.encodeToString(
            randomBytes,
            Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING
        )
    }
}

internal fun googleDriveBackupScope(): Scope = Scope(Scopes.DRIVE_APPFOLDER)

private suspend fun <T> Task<T>.awaitResult(): T {
    return suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            if (continuation.isActive) {
                continuation.resume(result)
            }
        }
        addOnFailureListener { error ->
            if (continuation.isActive) {
                continuation.resumeWithException(error)
            }
        }
        addOnCanceledListener {
            if (continuation.isActive) {
                continuation.cancel()
            }
        }
    }
}

private fun androidx.credentials.Credential.asGoogleIdTokenCredentialOrNull(): GoogleIdTokenCredential? {
    val customCredential = this as? CustomCredential ?: return null
    return if (customCredential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        GoogleIdTokenCredential.createFrom(customCredential.data)
    } else {
        null
    }
}
