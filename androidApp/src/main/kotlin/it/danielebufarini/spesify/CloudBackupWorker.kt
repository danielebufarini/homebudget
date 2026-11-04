package it.danielebufarini.spesify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import it.danielebufarini.spesify.data.AndroidCloudBackupStore
import it.danielebufarini.spesify.data.CloudSyncService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlinx.coroutines.CancellationException

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
            onFailure = { if (it is CancellationException) throw it else Result.retry() }
        )
    }
}
