package it.danielebufarini.spesify

import android.app.Application
import it.danielebufarini.spesify.data.notifications.AppWhitelistRepository
import it.danielebufarini.spesify.di.initKoin
import it.danielebufarini.spesify.notificationsync.AppWhitelistSyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.dsl.module

class SpesifyApplication : Application(), KoinComponent {
    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val appWhitelistRepository: AppWhitelistRepository by inject()

    override fun onCreate() {
        super.onCreate()

        initKoin(
            module {
                single<android.content.Context> { this@SpesifyApplication }
            }
        )
        CloudBackupWorkScheduler.schedule(this)
        AppWhitelistSyncScheduler.schedulePeriodic(this)
        startupScope.launch {
            runCatching {
                AppWhitelistSyncScheduler.enqueueColdStartSyncIfCacheEmptyOrStale(
                    context = this@SpesifyApplication,
                    repository = appWhitelistRepository
                )
            }
        }
    }
}
