package it.homebudget.app.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import it.homebudget.app.database.HomeBudgetDatabase
import it.homebudget.app.database.addHomeBudgetMigrations

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
