package it.homebudget.app.di

import it.homebudget.app.ui.screens.IosGroupedExpensesStore
import org.koin.dsl.module
import it.homebudget.app.data.DatabaseDriverFactory

actual val platformModule = module {
    single { DatabaseDriverFactory() }
    single { IosGroupedExpensesStore(get()) }
}
