package it.homebudget.app.di

import it.homebudget.app.data.DatabaseBuilderFactory
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

actual val platformModule = module {
    single { DatabaseBuilderFactory(androidContext()) }
}
