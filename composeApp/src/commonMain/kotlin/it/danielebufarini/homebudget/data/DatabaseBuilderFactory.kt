package it.danielebufarini.homebudget.data

import androidx.room.RoomDatabase
import it.danielebufarini.homebudget.database.HomeBudgetDatabase

expect class DatabaseBuilderFactory {
    fun createBuilder(): RoomDatabase.Builder<HomeBudgetDatabase>
}
