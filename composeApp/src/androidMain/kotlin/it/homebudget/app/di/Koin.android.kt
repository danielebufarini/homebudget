package it.homebudget.app.di

import org.koin.dsl.module
import it.homebudget.app.data.DatabaseDriverFactory
import org.koin.android.ext.koin.androidContext

actual val platformModule = module {
    single { DatabaseDriverFactory(androidContext()) }
}
