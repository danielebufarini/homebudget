package it.danielebufarini.spesify.di

import it.danielebufarini.spesify.AndroidStartupRestore
import it.danielebufarini.spesify.PlatformStartupRestore
import it.danielebufarini.spesify.data.AndroidCloudBackupStore
import it.danielebufarini.spesify.data.DashboardPreferencesStore
import it.danielebufarini.spesify.data.DatabaseBuilderFactory
import it.danielebufarini.spesify.data.GoogleDriveAuthorizationManager
import it.danielebufarini.spesify.data.PlatformDashboardPreferencesStore
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

actual val platformModule = module {
    single<PlatformStartupRestore> { AndroidStartupRestore(androidContext(), get(), get()) }
    single { GoogleDriveAuthorizationManager(androidContext()) }
    single { AndroidCloudBackupStore(androidContext(), get()) }
    single { DatabaseBuilderFactory(androidContext()) }
    single<DashboardPreferencesStore> { PlatformDashboardPreferencesStore(androidContext()) }
}
