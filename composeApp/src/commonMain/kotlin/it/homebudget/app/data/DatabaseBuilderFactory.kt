package it.homebudget.app.data

import androidx.room.RoomDatabase
import it.homebudget.app.database.HomeBudgetDatabase

expect class DatabaseBuilderFactory {
    fun createBuilder(): RoomDatabase.Builder<HomeBudgetDatabase>
}
