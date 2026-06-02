package it.danielebufarini.homebudget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import it.danielebufarini.homebudget.data.AndroidCloudBackupStore
import it.danielebufarini.homebudget.data.CloudSyncService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class CloudBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams), KoinComponent {

    private val cloudSyncService: CloudSyncService by inject()
    private val cloudBackupStore: AndroidCloudBackupStore by inject()

    override suspend fun doWork(): Result {
        return runCatching {
            val backup = cloudSyncService.buildBackupFile()
            cloudBackupStore.writeBackupFile(backup)
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }
}
