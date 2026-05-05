package it.homebudget.app.di

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import it.homebudget.app.data.DatabaseBuilderFactory
import it.homebudget.app.data.ExpenseRepository
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.module

expect val platformModule: Module

val sharedModule = module {
    single {
        val builderFactory = get<DatabaseBuilderFactory>()
        builderFactory.createBuilder()
            .setDriver(BundledSQLiteDriver())
            .build()
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
