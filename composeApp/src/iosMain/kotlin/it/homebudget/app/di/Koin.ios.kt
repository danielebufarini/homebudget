package it.homebudget.app.di

import it.homebudget.app.NoOpPlatformStartupRestore
import it.homebudget.app.PlatformStartupRestore
import it.homebudget.app.data.DashboardPreferencesStore
import it.homebudget.app.data.DatabaseBuilderFactory
import it.homebudget.app.data.PlatformDashboardPreferencesStore
import it.homebudget.app.ui.screens.IosGroupedExpensesStore
import org.koin.dsl.module

actual val platformModule = module {
    single<PlatformStartupRestore> { NoOpPlatformStartupRestore }
    single { DatabaseBuilderFactory() }
    single<DashboardPreferencesStore> { PlatformDashboardPreferencesStore() }
    single { IosGroupedExpensesStore(get()) }
}
