package it.homebudget.app

import android.app.Application
import it.homebudget.app.di.initKoin
import org.koin.dsl.module

class HomeBudgetApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        initKoin(
            module {
                single<android.content.Context> { this@HomeBudgetApplication }
            }
        )
    }
}
