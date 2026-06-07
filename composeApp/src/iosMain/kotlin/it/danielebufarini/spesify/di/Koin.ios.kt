package it.danielebufarini.spesify.di

import it.danielebufarini.spesify.NoOpPlatformStartupRestore
import it.danielebufarini.spesify.PlatformStartupRestore
import it.danielebufarini.spesify.data.DashboardPreferencesStore
import it.danielebufarini.spesify.data.DatabaseBuilderFactory
import it.danielebufarini.spesify.data.PlatformDashboardPreferencesStore
import org.koin.dsl.module

actual val platformModule = module {
    single<PlatformStartupRestore> { NoOpPlatformStartupRestore }
    single { DatabaseBuilderFactory() }
    single<DashboardPreferencesStore> { PlatformDashboardPreferencesStore() }
}
