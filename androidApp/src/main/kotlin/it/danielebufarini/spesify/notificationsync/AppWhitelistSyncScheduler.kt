package it.danielebufarini.spesify.notificationsync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import it.danielebufarini.spesify.data.notifications.AppWhitelistRepository
import java.time.Duration
import java.util.concurrent.TimeUnit

object AppWhitelistSyncScheduler {
    private const val PERIODIC_WORK_NAME = "app-whitelist-sync-periodic"
    private const val COLD_START_WORK_NAME = "app-whitelist-sync-cold-start"

    fun schedulePeriodic(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicWork = PeriodicWorkRequestBuilder<AppWhitelistSyncWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofMinutes(30))
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicWork
        )
    }

    suspend fun enqueueColdStartSyncIfCacheEmptyOrStale(
        context: Context,
        repository: AppWhitelistRepository
    ) {
        if (!repository.isCacheEmptyOrStale()) return

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val oneTimeWork = OneTimeWorkRequestBuilder<AppWhitelistSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofMinutes(10))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            COLD_START_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            oneTimeWork
        )
    }
}
