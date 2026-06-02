package it.danielebufarini.homebudget

import android.app.Application
import it.danielebufarini.homebudget.di.initKoin
import org.koin.dsl.module

class HomeBudgetApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        initKoin(
            module {
                single<android.content.Context> { this@HomeBudgetApplication }
            }
        )
        CloudBackupWorkScheduler.schedule(this)
    }
}
