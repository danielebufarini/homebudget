package it.homebudget.app

import android.content.Context
import it.homebudget.app.data.AndroidCloudBackupStore
import it.homebudget.app.data.CloudSyncService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AndroidStartupRestore(
    private val context: Context,
    private val cloudSyncService: CloudSyncService,
    private val cloudBackupStore: AndroidCloudBackupStore
) : PlatformStartupRestore {
    private var pendingBackupText: String? = null

    override suspend fun prepare(): StartupRestoreState {
        if (restoreMarkerExists()) {
            return StartupRestoreState.Ready
        }

        if (!cloudSyncService.isRestoreTargetEmpty()) {
            markRestoreCompleted()
            return StartupRestoreState.Ready
        }

        val backupText = cloudBackupStore.readLocalBackupFile() ?: return StartupRestoreState.Ready
        val preview = cloudSyncService.previewRestore(backupText)
        pendingBackupText = backupText
        return StartupRestoreState.Pending(
            StartupRestorePrompt(preview = preview)
        )
    }

    override suspend fun restorePending() {
        val backupText = pendingBackupText ?: return
        cloudSyncService.restoreFromBackup(backupText)
        pendingBackupText = null
        markRestoreCompleted()
    }

    override suspend fun skipPending() {
        pendingBackupText = null
        markRestoreCompleted()
    }

    private suspend fun restoreMarkerExists(): Boolean {
        return withContext(Dispatchers.IO) {
            restoreMarkerFile().exists()
        }
    }

    private suspend fun markRestoreCompleted() {
        withContext(Dispatchers.IO) {
            val markerFile = restoreMarkerFile()
            markerFile.parentFile?.mkdirs()
            markerFile.writeText("completed", Charsets.UTF_8)
        }
    }

    private fun restoreMarkerFile(): File {
        return File(context.noBackupFilesDir, "startup-restore/completed.marker")
    }
}
