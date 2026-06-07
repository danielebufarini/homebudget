package it.danielebufarini.spesify.data

import androidx.room.RoomDatabase
import it.danielebufarini.spesify.database.SpesifyDatabase

expect class DatabaseBuilderFactory {
    fun createBuilder(): RoomDatabase.Builder<SpesifyDatabase>
}
