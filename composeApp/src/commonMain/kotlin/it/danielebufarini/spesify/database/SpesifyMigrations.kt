package it.danielebufarini.spesify.database

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


internal val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `recurring_transaction_rule` (
                `id` TEXT NOT NULL,
                `kind` TEXT NOT NULL,
                `amount` INTEGER NOT NULL,
                `startDate` INTEGER NOT NULL,
                `frequency` TEXT NOT NULL DEFAULT 'monthly',
                `intervalMonths` INTEGER NOT NULL DEFAULT 1,
                `generatedThroughYearMonth` INTEGER NOT NULL DEFAULT 0,
                `categoryId` TEXT DEFAULT NULL,
                `description` TEXT,
                `isShared` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`categoryId`) REFERENCES `category`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_recurring_transaction_rule_kind` " +
                "ON `recurring_transaction_rule` (`kind`)"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_recurring_transaction_rule_categoryId` " +
                "ON `recurring_transaction_rule` (`categoryId`)"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_recurring_transaction_rule_generatedThroughYearMonth` " +
                "ON `recurring_transaction_rule` (`generatedThroughYearMonth`)"
        )
        connection.execSQL(
            """
            INSERT OR IGNORE INTO `recurring_transaction_rule` (
                `id`,
                `kind`,
                `amount`,
                `startDate`,
                `frequency`,
                `intervalMonths`,
                `generatedThroughYearMonth`,
                `categoryId`,
                `description`,
                `isShared`
            )
            SELECT
                e.`recurringSeriesId`,
                'expense',
                e.`amount`,
                e.`date`,
                'monthly',
                1,
                grouped.`generatedThroughYearMonth`,
                e.`categoryId`,
                e.`description`,
                e.`isShared`
            FROM `expense` e
            JOIN (
                SELECT
                    `recurringSeriesId`,
                    MIN(`date`) AS `startDate`,
                    MAX(`yearMonth`) AS `generatedThroughYearMonth`
                FROM `expense`
                WHERE `recurringSeriesId` IS NOT NULL
                  AND `recurringSeriesId` != ''
                GROUP BY `recurringSeriesId`
            ) grouped
              ON grouped.`recurringSeriesId` = e.`recurringSeriesId`
             AND grouped.`startDate` = e.`date`
            WHERE e.`recurringSeriesId` IS NOT NULL
              AND e.`recurringSeriesId` != ''
            GROUP BY e.`recurringSeriesId`
            """.trimIndent()
        )
        connection.execSQL(
            """
            INSERT OR IGNORE INTO `recurring_transaction_rule` (
                `id`,
                `kind`,
                `amount`,
                `startDate`,
                `frequency`,
                `intervalMonths`,
                `generatedThroughYearMonth`,
                `categoryId`,
                `description`,
                `isShared`
            )
            SELECT
                i.`recurringSeriesId`,
                'income',
                i.`amount`,
                i.`date`,
                'monthly',
                1,
                grouped.`generatedThroughYearMonth`,
                i.`categoryId`,
                i.`description`,
                0
            FROM `income` i
            JOIN (
                SELECT
                    `recurringSeriesId`,
                    MIN(`date`) AS `startDate`,
                    MAX(`yearMonth`) AS `generatedThroughYearMonth`
                FROM `income`
                WHERE `recurringSeriesId` IS NOT NULL
                  AND `recurringSeriesId` != ''
                GROUP BY `recurringSeriesId`
            ) grouped
              ON grouped.`recurringSeriesId` = i.`recurringSeriesId`
             AND grouped.`startDate` = i.`date`
            WHERE i.`recurringSeriesId` IS NOT NULL
              AND i.`recurringSeriesId` != ''
            GROUP BY i.`recurringSeriesId`
            """.trimIndent()
        )
    }
}

internal fun RoomDatabase.Builder<SpesifyDatabase>.addSpesifyMigrations():
    RoomDatabase.Builder<SpesifyDatabase> = this
        .addMigrations(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8
        )
