package it.homebudget.app.di

import it.homebudget.app.data.DatabaseBuilderFactory
import it.homebudget.app.ui.screens.IosGroupedExpensesStore
import org.koin.dsl.module

actual val platformModule = module {
    single { DatabaseBuilderFactory() }
    single { IosGroupedExpensesStore(get()) }
}
