package it.danielebufarini.homebudget.di

import it.danielebufarini.homebudget.AndroidStartupRestore
import it.danielebufarini.homebudget.PlatformStartupRestore
import it.danielebufarini.homebudget.data.AndroidCloudBackupStore
import it.danielebufarini.homebudget.data.DashboardPreferencesStore
import it.danielebufarini.homebudget.data.DatabaseBuilderFactory
import it.danielebufarini.homebudget.data.GoogleDriveAuthorizationManager
import it.danielebufarini.homebudget.data.PlatformDashboardPreferencesStore
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

actual val platformModule = module {
    single<PlatformStartupRestore> { AndroidStartupRestore(androidContext(), get(), get()) }
    single { GoogleDriveAuthorizationManager(androidContext()) }
    single { AndroidCloudBackupStore(androidContext(), get()) }
    single { DatabaseBuilderFactory(androidContext()) }
    single<DashboardPreferencesStore> { PlatformDashboardPreferencesStore(androidContext()) }
}
