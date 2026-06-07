package it.danielebufarini.spesify

import android.app.Application
import it.danielebufarini.spesify.di.initKoin
import org.koin.dsl.module

class SpesifyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        initKoin(
            module {
                single<android.content.Context> { this@SpesifyApplication }
            }
        )
        CloudBackupWorkScheduler.schedule(this)
    }
}
