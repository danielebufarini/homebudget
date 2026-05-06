package it.homebudget.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AndroidCloudBackupStore(
    private val context: Context,
    private val authorizationManager: GoogleDriveAuthorizationManager
) {
    suspend fun writeBackupFile(backup: BudgetBackupFile) {
        writeBackupAtomically(backup.fileName, backup.content)
        authorizationManager.getAuthorizedAccessTokenOrNull()?.let { accessToken ->
            uploadBackupToGoogleDrive(accessToken, backup)
        }
    }

    suspend fun readBackupFile(fileName: String = BACKUP_FILE_NAME): String? {
        val driveBackup = authorizationManager.getAuthorizedAccessTokenOrNull()?.let { accessToken ->
            downloadBackupFromGoogleDrive(accessToken, fileName)
        }
        return driveBackup ?: readLocalBackup(fileName)
    }

    suspend fun readLocalBackupFile(fileName: String = BACKUP_FILE_NAME): String? {
        return readLocalBackup(fileName)
    }

    suspend fun localBackupExists(fileName: String = BACKUP_FILE_NAME): Boolean {
        return withContext(Dispatchers.IO) {
            backupFile(fileName).exists()
        }
    }

    suspend fun writeBackupAtomically(
        fileName: String,
        content: String
    ) {
        withContext(Dispatchers.IO) {
            val destination = backupFile(fileName)
            val directory = requireNotNull(destination.parentFile)
            if (!directory.exists()) {
                directory.mkdirs()
            }

            val temporary = File(directory, "$fileName.tmp")
            temporary.writeText(content, Charsets.UTF_8)
            if (destination.exists()) {
                destination.delete()
            }
            if (!temporary.renameTo(destination)) {
                temporary.copyTo(destination, overwrite = true)
                temporary.delete()
            }
        }
    }

    private suspend fun readLocalBackup(fileName: String): String? {
        return withContext(Dispatchers.IO) {
            val backupFile = backupFile(fileName)
            if (backupFile.exists()) {
                backupFile.readText(Charsets.UTF_8)
            } else {
                null
            }
        }
    }

    private fun backupFile(fileName: String): File {
        return File(context.filesDir, "$CLOUD_BACKUP_DIRECTORY_NAME/$fileName")
    }
}
