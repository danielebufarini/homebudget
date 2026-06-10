package it.danielebufarini.spesify.notificationsync

import android.content.Context
import android.util.Log
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
        Log.d(TAG, "Whitelist WorkManager sync started")
        val refreshResult = appWhitelistRepository.refreshWhitelist()
        return refreshResult.fold(
            onSuccess = {
                Log.d(TAG, "Whitelist WorkManager sync succeeded")
                Result.success()
            },
            onFailure = { error ->
                val transient = error.isTransientAppWhitelistFailure()
                Log.e(
                    TAG,
                    "Whitelist WorkManager sync failed transient=$transient type=${error::class.java.simpleName} message=${error.message}",
                    error
                )
                if (transient) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }
        )
    }

    private companion object {
        const val TAG = "SpesifyNotifDetect"
    }
}
