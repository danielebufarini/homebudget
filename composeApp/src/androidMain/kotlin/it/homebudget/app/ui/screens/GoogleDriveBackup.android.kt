package it.homebudget.app.ui.screens

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import it.homebudget.app.data.BudgetBackupFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private const val GoogleDriveBackupFileName = "homebudget-backup.json"
private const val GoogleDriveBackupMimeType = "application/json"
private const val GoogleDriveAppName = "HomeBudget"

internal fun googleDriveBackupScope(): Scope = Scope(Scopes.DRIVE_APPFOLDER)

internal fun createGoogleDriveBackupSignInClient(context: Context): GoogleSignInClient {
    val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(googleDriveBackupScope())
        .build()
    return GoogleSignIn.getClient(context, options)
}

internal fun hasGoogleDriveBackupAccess(account: GoogleSignInAccount?): Boolean {
    return account != null && GoogleSignIn.hasPermissions(account, googleDriveBackupScope())
}

internal suspend fun uploadBackupToGoogleDrive(
    context: Context,
    account: GoogleSignInAccount,
    backup: BudgetBackupFile
) {
    withContext(Dispatchers.IO) {
        val drive = createGoogleDriveService(context, account)
        val existingFileId = findGoogleDriveBackupFileId(drive)
        val mediaContent = ByteArrayContent(
            GoogleDriveBackupMimeType,
            backup.content.encodeToByteArray()
        )

        if (existingFileId == null) {
            val metadata = File().apply {
                name = GoogleDriveBackupFileName
                parents = listOf("appDataFolder")
            }
            drive.files()
                .create(metadata, mediaContent)
                .setFields("id")
                .execute()
        } else {
            drive.files()
                .update(existingFileId, null, mediaContent)
                .setFields("id")
                .execute()
        }
    }
}

internal suspend fun downloadBackupFromGoogleDrive(
    context: Context,
    account: GoogleSignInAccount
): String {
    return withContext(Dispatchers.IO) {
        val drive = createGoogleDriveService(context, account)
        val fileId = findGoogleDriveBackupFileId(drive)
            ?: throw NoSuchElementException("CLOUD_BACKUP_NOT_FOUND")

        val output = ByteArrayOutputStream()
        drive.files()
            .get(fileId)
            .executeMediaAndDownloadTo(output)

        output.toString(Charsets.UTF_8.name())
    }
}

private fun createGoogleDriveService(
    context: Context,
    account: GoogleSignInAccount
): Drive {
    val selectedAccount = requireNotNull(account.account) {
        "No Google account is available for Drive backup."
    }
    val credential = GoogleAccountCredential.usingOAuth2(
        context,
        setOf(Scopes.DRIVE_APPFOLDER)
    ).apply {
        selectedAccountName = selectedAccount.name
    }

    return Drive.Builder(
        GoogleNetHttpTransport.newTrustedTransport(),
        GsonFactory.getDefaultInstance(),
        credential
    ).setApplicationName(GoogleDriveAppName)
        .build()
}

private fun findGoogleDriveBackupFileId(drive: Drive): String? {
    return drive.files()
        .list()
        .setSpaces("appDataFolder")
        .setQ("name = '$GoogleDriveBackupFileName' and trashed = false")
        .setFields("files(id, name)")
        .execute()
        .files
        .orEmpty()
        .firstOrNull()
        ?.id
}
