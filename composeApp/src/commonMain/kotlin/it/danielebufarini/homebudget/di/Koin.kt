package it.danielebufarini.homebudget.di

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import it.danielebufarini.homebudget.data.BackupRestoreService
import it.danielebufarini.homebudget.data.BudgetDataReplacementRepository
import it.danielebufarini.homebudget.data.CategoryManagementRepository
import it.danielebufarini.homebudget.data.CategoryRepository
import it.danielebufarini.homebudget.data.CloudSyncService
import it.danielebufarini.homebudget.data.DashboardReadRepository
import it.danielebufarini.homebudget.data.DashboardRepository
import it.danielebufarini.homebudget.data.DataReplacementService
import it.danielebufarini.homebudget.data.DatabaseBuilderFactory
import it.danielebufarini.homebudget.data.DatabaseTransactionRunner
import it.danielebufarini.homebudget.data.ExpenseEntryRepository
import it.danielebufarini.homebudget.data.ExpenseReadRepository
import it.danielebufarini.homebudget.data.ExpenseRepository
import it.danielebufarini.homebudget.data.ExpenseRepositoryBudgetDataReplacementAdapter
import it.danielebufarini.homebudget.data.ExpenseRepositoryCategoryManagementAdapter
import it.danielebufarini.homebudget.data.ExpenseRepositoryDashboardReadAdapter
import it.danielebufarini.homebudget.data.ExpenseRepositoryIncomeReadAdapter
import it.danielebufarini.homebudget.data.ExpenseRepositoryReadAdapter
import it.danielebufarini.homebudget.data.ExpenseRepositoryTransactionWriteAdapter
import it.danielebufarini.homebudget.data.IncomeReadRepository
import it.danielebufarini.homebudget.data.IncomeRepository
import it.danielebufarini.homebudget.data.PersistentWriteScope
import it.danielebufarini.homebudget.data.RecurringTransactionService
import it.danielebufarini.homebudget.data.TransactionWriteRepository
import it.danielebufarini.homebudget.data.WidgetRefreshCoordinator
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
    single { PersistentWriteScope(databaseQueryCoroutineContext) }
    single { CategoryRepository(get(), get()) }
    single { ExpenseEntryRepository(get(), get(), get()) }
    single { IncomeRepository(get(), get(), get()) }
    single { DashboardRepository(get()) }
    single { RecurringTransactionService(get(), get(), get()) }
    single { DataReplacementService(get(), get(), get()) }
    single { ExpenseRepository(get(), get(), get(), get(), get(), get()) }
    single<CategoryManagementRepository> { ExpenseRepositoryCategoryManagementAdapter(get()) }
    single<ExpenseReadRepository> { ExpenseRepositoryReadAdapter(get()) }
    single<IncomeReadRepository> { ExpenseRepositoryIncomeReadAdapter(get()) }
    single<DashboardReadRepository> { ExpenseRepositoryDashboardReadAdapter(get()) }
    single<TransactionWriteRepository> { ExpenseRepositoryTransactionWriteAdapter(get()) }
    single<BudgetDataReplacementRepository> { ExpenseRepositoryBudgetDataReplacementAdapter(get()) }
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
