package it.danielebufarini.homebudget.data

import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.HttpRequest
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private const val GOOGLE_DRIVE_BACKUP_MIME_TYPE = "application/json"
private const val GOOGLE_DRIVE_APP_NAME = "HomeBudget"

internal suspend fun uploadBackupToGoogleDrive(
    accessToken: String,
    backup: BudgetBackupFile
) {
    withContext(Dispatchers.IO) {
        val drive = createGoogleDriveService(accessToken)
        val existingFileId = findGoogleDriveBackupFileId(drive, backup.fileName)
        val mediaContent = ByteArrayContent(
            GOOGLE_DRIVE_BACKUP_MIME_TYPE,
            backup.content.encodeToByteArray()
        )

        if (existingFileId == null) {
            val metadata = File().apply {
                name = backup.fileName
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
    accessToken: String,
    fileName: String
): String? {
    return withContext(Dispatchers.IO) {
        val drive = createGoogleDriveService(accessToken)
        val fileId = findGoogleDriveBackupFileId(drive, fileName) ?: return@withContext null

        val output = ByteArrayOutputStream()
        drive.files()
            .get(fileId)
            .executeMediaAndDownloadTo(output)

        output.toString(Charsets.UTF_8.name())
    }
}

private val httpTransport by lazy {
    NetHttpTransport()
}

private val jsonFactory by lazy {
    GsonFactory.getDefaultInstance()
}

private fun createGoogleDriveService(accessToken: String): Drive {
    val requestInitializer = HttpRequestInitializer { request: HttpRequest ->
        request.headers.authorization = "Bearer $accessToken"
    }

    return Drive.Builder(
        httpTransport,
        jsonFactory,
        requestInitializer
    )
        .setApplicationName(GOOGLE_DRIVE_APP_NAME)
        .build()
}

private fun findGoogleDriveBackupFileId(
    drive: Drive,
    fileName: String
): String? {
    return drive.files()
        .list()
        .setSpaces("appDataFolder")
        .setQ("name = '$fileName' and trashed = false")
        .setFields("files(id, name)")
        .execute()
        .files
        .orEmpty()
        .firstOrNull()
        ?.id
}
