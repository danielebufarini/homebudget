package it.homebudget.app.database

import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_expense_recurringSeriesId_date` " +
                "ON `expense` (`recurringSeriesId`, `date`)"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_income_recurringSeriesId_date` " +
                "ON `income` (`recurringSeriesId`, `date`)"
        )
    }
}

internal fun RoomDatabase.Builder<HomeBudgetDatabase>.addHomeBudgetMigrations():
    RoomDatabase.Builder<HomeBudgetDatabase> = addMigrations(MIGRATION_1_2)
