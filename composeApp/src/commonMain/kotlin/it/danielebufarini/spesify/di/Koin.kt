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
import it.danielebufarini.spesify.data.BudgetDataReplacementRepositoryAdapter
import it.danielebufarini.spesify.data.CategoryManagementRepositoryAdapter
import it.danielebufarini.spesify.data.DashboardReadRepositoryAdapter
import it.danielebufarini.spesify.data.IncomeReadRepositoryAdapter
import it.danielebufarini.spesify.data.ExpenseReadRepositoryAdapter
import it.danielebufarini.spesify.data.TransactionWriteRepositoryAdapter
import it.danielebufarini.spesify.data.FinancialQueryUseCase
import it.danielebufarini.spesify.data.IncomeReadRepository
import it.danielebufarini.spesify.data.IncomeRepository
import it.danielebufarini.spesify.data.PersistentWriteScope
import it.danielebufarini.spesify.data.RecurringTransactionService
import it.danielebufarini.spesify.data.TransactionWriteRepository
import it.danielebufarini.spesify.data.TransactionEditorService
import it.danielebufarini.spesify.data.WidgetRefreshCoordinator
import it.danielebufarini.spesify.data.notifications.ExpenseInterpretationPipeline
import it.danielebufarini.spesify.data.notifications.InterpretExpenseTextUseCase
import it.danielebufarini.spesify.data.notifications.LlmExpenseJsonValidator
import it.danielebufarini.spesify.data.notifications.LocalExpenseTextLlmInterpreter
import it.danielebufarini.spesify.data.notifications.RegexExpenseTextInterpreter
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
    single { TransactionEditorService(get()) }
    single { AddTransactionUseCase(get<CategoryManagementRepository>(), get<TransactionWriteRepository>(), get()) }
    single { CategoryAgentUseCase(get<CategoryManagementRepository>()) }
    single { FinancialQueryUseCase(get(), get()) }
    single { RegexExpenseTextInterpreter() }
    single { LlmExpenseJsonValidator() }
    single {
        ExpenseInterpretationPipeline(
            regexInterpreter = get<RegexExpenseTextInterpreter>(),
            localLlmInterpreter = runCatching { get<LocalExpenseTextLlmInterpreter>() }.getOrNull(),
            llmJsonValidator = get<LlmExpenseJsonValidator>()
        )
    }
    single { InterpretExpenseTextUseCase(get()) }
    single { DataReplacementService(get(), get(), get()) }
    single { ExpenseRepository(get(), get(), get(), get(), get(), get()) }
    single<CategoryManagementRepository> { CategoryManagementRepositoryAdapter(get()) }
    single<ExpenseReadRepository> { ExpenseReadRepositoryAdapter(get()) }
    single<IncomeReadRepository> { IncomeReadRepositoryAdapter(get()) }
    single<DashboardReadRepository> { DashboardReadRepositoryAdapter(get()) }
    single<TransactionWriteRepository> { TransactionWriteRepositoryAdapter(get(), get(), get()) }
    single<BudgetDataReplacementRepository> { BudgetDataReplacementRepositoryAdapter(get()) }
    single { BackupRestoreService(get()) }
    single { CloudSyncService(get()) }
}

fun initKoin(appModule: Module? = null) {
    startKoin {
        modules(
            listOfNotNull(
                sharedModule,
                platformModule,
                appModule
            )
        )
    }
}
