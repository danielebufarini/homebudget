package it.homebudget.app.di

import org.koin.dsl.module
import it.homebudget.app.data.DatabaseDriverFactory

actual val platformModule = module {
    single { DatabaseDriverFactory() }
}
