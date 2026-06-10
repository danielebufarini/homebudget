package it.danielebufarini.spesify.notificationsync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import it.danielebufarini.spesify.data.notifications.AppWhitelistRepository
import it.danielebufarini.spesify.data.notifications.isTransientAppWhitelistFailure
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AppWhitelistSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams), KoinComponent {

    private val appWhitelistRepository: AppWhitelistRepository by inject()

    override suspend fun doWork(): Result {
        val refreshResult = appWhitelistRepository.refreshWhitelist()
        return refreshResult.fold(
            onSuccess = { Result.success() },
            onFailure = { error ->
                if (error.isTransientAppWhitelistFailure()) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }
        )
    }
}
