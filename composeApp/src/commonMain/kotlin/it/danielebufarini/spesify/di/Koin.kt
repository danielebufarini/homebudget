package it.danielebufarini.spesify.di

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import it.danielebufarini.spesify.data.AddTransactionUseCase
import it.danielebufarini.spesify.data.BackupRestoreService
import it.danielebufarini.spesify.data.BudgetDataReplacementRepository
import it.danielebufarini.spesify.data.CategoryAgentUseCase
import it.danielebufarini.spesify.data.CategoryManagementRepository
import it.danielebufarini.spesify.data.CategoryRepository
import it.danielebufarini.spesify.data.CloudSyncService
import it.danielebufarini.spesify.data.DashboardReadRepository
import it.danielebufarini.spesify.data.DashboardRepository
import it.danielebufarini.spesify.data.DataReplacementService
import it.danielebufarini.spesify.data.DatabaseBuilderFactory
import it.danielebufarini.spesify.data.DatabaseTransactionRunner
import it.danielebufarini.spesify.data.ExpenseEntryRepository
import it.danielebufarini.spesify.data.ExpenseReadRepository
import it.danielebufarini.spesify.data.ExpenseRepository
import it.danielebufarini.spesify.data.ExpenseRepositoryBudgetDataReplacementAdapter
import it.danielebufarini.spesify.data.ExpenseRepositoryCategoryManagementAdapter
import it.danielebufarini.spesify.data.ExpenseRepositoryDashboardReadAdapter
import it.danielebufarini.spesify.data.ExpenseRepositoryIncomeReadAdapter
import it.danielebufarini.spesify.data.ExpenseRepositoryReadAdapter
import it.danielebufarini.spesify.data.ExpenseRepositoryTransactionWriteAdapter
import it.danielebufarini.spesify.data.FinancialQueryUseCase
import it.danielebufarini.spesify.data.IncomeReadRepository
import it.danielebufarini.spesify.data.IncomeRepository
import it.danielebufarini.spesify.data.PersistentWriteScope
import it.danielebufarini.spesify.data.RecurringTransactionService
import it.danielebufarini.spesify.data.TransactionWriteRepository
import it.danielebufarini.spesify.data.WidgetRefreshCoordinator
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
    single { AddTransactionUseCase(get<CategoryManagementRepository>(), get<TransactionWriteRepository>()) }
    single { CategoryAgentUseCase(get<CategoryManagementRepository>()) }
    single { FinancialQueryUseCase(get(), get()) }
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
