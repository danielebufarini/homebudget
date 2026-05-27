package it.homebudget.app.di

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import it.homebudget.app.data.BackupRestoreService
import it.homebudget.app.data.CategoryRepository
import it.homebudget.app.data.CloudSyncService
import it.homebudget.app.data.DashboardRepository
import it.homebudget.app.data.DataReplacementService
import it.homebudget.app.data.DatabaseBuilderFactory
import it.homebudget.app.data.DatabaseTransactionRunner
import it.homebudget.app.data.ExpenseEntryRepository
import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.data.IncomeRepository
import it.homebudget.app.data.RecurringTransactionService
import it.homebudget.app.data.WidgetRefreshCoordinator
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.module

expect val platformModule: Module

val sharedModule = module {
    single {
        val builderFactory = get<DatabaseBuilderFactory>()
        builderFactory.createBuilder()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(databaseQueryCoroutineContext)
            .build()
    }
    single { DatabaseTransactionRunner(get()) }
    single { WidgetRefreshCoordinator() }
    single { CategoryRepository(get(), get()) }
    single { ExpenseEntryRepository(get(), get(), get()) }
    single { IncomeRepository(get(), get(), get()) }
    single { DashboardRepository(get()) }
    single { RecurringTransactionService(get(), get(), get()) }
    single { DataReplacementService(get(), get(), get()) }
    single { ExpenseRepository(get(), get(), get(), get(), get(), get()) }
    single { BackupRestoreService(get()) }
    single { CloudSyncService(get()) }
}

fun initKoin(appModule: Module? = null) {
    startKoin {
        modules(
            listOfNotNull(
                platformModule,
                sharedModule,
                appModule
            )
        )
    }
}
