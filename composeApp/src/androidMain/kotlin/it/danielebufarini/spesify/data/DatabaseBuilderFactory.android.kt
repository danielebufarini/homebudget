package it.danielebufarini.spesify.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import it.danielebufarini.spesify.database.SpesifyDatabase
import it.danielebufarini.spesify.database.addSpesifyMigrations

actual class DatabaseBuilderFactory(private val context: Context) {
    actual fun createBuilder(): RoomDatabase.Builder<SpesifyDatabase> {
        val appContext = context.applicationContext
        val dbFile = appContext.getDatabasePath("spesify-room.db")
        return Room.databaseBuilder<SpesifyDatabase>(
            context = appContext,
            name = dbFile.absolutePath
        ).addSpesifyMigrations()
    }
}
