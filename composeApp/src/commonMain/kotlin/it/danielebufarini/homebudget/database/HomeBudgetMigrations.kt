package it.danielebufarini.homebudget.database

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

internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        migrateExpenseTable(connection)
        migrateIncomeTable(connection)
    }
}


internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `category` ADD COLUMN `color` TEXT NOT NULL DEFAULT '#6F45E9'")
        connection.execSQL("ALTER TABLE `category` ADD COLUMN `categoryType` TEXT NOT NULL DEFAULT 'expense'")
        connection.execSQL("ALTER TABLE `category` ADD COLUMN `isArchived` INTEGER NOT NULL DEFAULT 0")
        migrateIncomeTableForCategories(connection)
    }
}

internal val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        migrateCategoryTableForSortOrder(connection)
    }
}

internal val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(connection: SQLiteConnection) {
        addStoredDateKeys(
            connection = connection,
            tableName = "expense",
            createIndexes = listOf(
                "CREATE INDEX IF NOT EXISTS `index_expense_yearMonth` ON `expense` (`yearMonth`)",
                "CREATE INDEX IF NOT EXISTS `index_expense_yearMonth_categoryId` " +
                    "ON `expense` (`yearMonth`, `categoryId`)",
                "CREATE INDEX IF NOT EXISTS `index_expense_yearMonth_isShared` " +
                    "ON `expense` (`yearMonth`, `isShared`)",
                "CREATE INDEX IF NOT EXISTS `index_expense_localDate` ON `expense` (`localDate`)"
            )
        )
        addStoredDateKeys(
            connection = connection,
            tableName = "income",
            createIndexes = listOf(
                "CREATE INDEX IF NOT EXISTS `index_income_yearMonth` ON `income` (`yearMonth`)",
                "CREATE INDEX IF NOT EXISTS `index_income_yearMonth_categoryId` " +
                    "ON `income` (`yearMonth`, `categoryId`)",
                "CREATE INDEX IF NOT EXISTS `index_income_localDate` ON `income` (`localDate`)"
            )
        )
    }
}

internal val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(connection: SQLiteConnection) {
        createExpenseSearchTable(connection)
        createIncomeSearchTable(connection)
        populateExpenseSearchIndex(connection)
        populateIncomeSearchIndex(connection)
    }
}

internal fun RoomDatabase.Builder<HomeBudgetDatabase>.addHomeBudgetMigrations():
    RoomDatabase.Builder<HomeBudgetDatabase> = this
        .addMigrations(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7
        )
