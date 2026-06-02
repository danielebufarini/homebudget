package it.danielebufarini.homebudget.di

import it.danielebufarini.homebudget.NoOpPlatformStartupRestore
import it.danielebufarini.homebudget.PlatformStartupRestore
import it.danielebufarini.homebudget.data.DashboardPreferencesStore
import it.danielebufarini.homebudget.data.DatabaseBuilderFactory
import it.danielebufarini.homebudget.data.PlatformDashboardPreferencesStore
import org.koin.dsl.module

actual val platformModule = module {
    single<PlatformStartupRestore> { NoOpPlatformStartupRestore }
    single { DatabaseBuilderFactory() }
    single<DashboardPreferencesStore> { PlatformDashboardPreferencesStore() }
}
