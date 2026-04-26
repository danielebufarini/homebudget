package it.homebudget.app.di

import it.homebudget.app.data.DatabaseDriverFactory
import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.data.BigIntegerColumnAdapter
import it.homebudget.app.database.HomeBudgetDatabase
import it.homebudget.app.database.Expense
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.module

expect val platformModule: Module

val sharedModule = module {
    single {
        val driverFactory = get<DatabaseDriverFactory>()
        HomeBudgetDatabase(
            driver = driverFactory.createDriver(),
            expenseAdapter = Expense.Adapter(
                amountAdapter = BigIntegerColumnAdapter
            )
        )
    }
    single { ExpenseRepository(get()) }
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
