package it.homebudget.app.di

import it.homebudget.app.AndroidStartupRestore
import it.homebudget.app.PlatformStartupRestore
import it.homebudget.app.data.AndroidCloudBackupStore
import it.homebudget.app.data.DashboardPreferencesStore
import it.homebudget.app.data.DatabaseBuilderFactory
import it.homebudget.app.data.GoogleDriveAuthorizationManager
import it.homebudget.app.data.PlatformDashboardPreferencesStore
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

actual val platformModule = module {
    single<PlatformStartupRestore> { AndroidStartupRestore(androidContext(), get(), get()) }
    single { GoogleDriveAuthorizationManager(androidContext()) }
    single { AndroidCloudBackupStore(androidContext(), get()) }
    single { DatabaseBuilderFactory(androidContext()) }
    single<DashboardPreferencesStore> { PlatformDashboardPreferencesStore(androidContext()) }
}
