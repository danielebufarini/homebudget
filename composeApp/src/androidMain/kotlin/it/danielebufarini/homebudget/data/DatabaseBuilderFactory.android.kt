package it.danielebufarini.homebudget.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import it.danielebufarini.homebudget.database.HomeBudgetDatabase
import it.danielebufarini.homebudget.database.addHomeBudgetMigrations

actual class DatabaseBuilderFactory(private val context: Context) {
    actual fun createBuilder(): RoomDatabase.Builder<HomeBudgetDatabase> {
        val appContext = context.applicationContext
        val dbFile = appContext.getDatabasePath("homebudget-room.db")
        return Room.databaseBuilder<HomeBudgetDatabase>(
            context = appContext,
            name = dbFile.absolutePath
        ).addHomeBudgetMigrations()
    }
}
