package it.homebudget.app.database

import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
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
        .addMigrations(MIGRATION_5_6, MIGRATION_6_7)
        .fallbackToDestructiveMigration(dropAllTables = true)

private fun createExpenseSearchTable(connection: SQLiteConnection) {
    connection.execSQL(
        """
        CREATE VIRTUAL TABLE IF NOT EXISTS `expense_search_fts` USING FTS4(
            `transactionId` TEXT NOT NULL,
            `categoryId` TEXT,
            `categoryName` TEXT NOT NULL,
            `description` TEXT NOT NULL,
            `amountText` TEXT NOT NULL,
            `amountMinorText` TEXT NOT NULL,
            `dateText` TEXT NOT NULL,
            `localDateText` TEXT NOT NULL,
            `yearMonthText` TEXT NOT NULL,
            tokenize=unicode61,
            notindexed=`transactionId`,
            notindexed=`categoryId`,
            prefix=`2,3,4`
        )
        """.trimIndent()
    )
}

private fun createIncomeSearchTable(connection: SQLiteConnection) {
    connection.execSQL(
        """
        CREATE VIRTUAL TABLE IF NOT EXISTS `income_search_fts` USING FTS4(
            `transactionId` TEXT NOT NULL,
            `categoryId` TEXT,
            `categoryName` TEXT NOT NULL,
            `description` TEXT NOT NULL,
            `amountText` TEXT NOT NULL,
            `amountMinorText` TEXT NOT NULL,
            `dateText` TEXT NOT NULL,
            `localDateText` TEXT NOT NULL,
            `yearMonthText` TEXT NOT NULL,
            tokenize=unicode61,
            notindexed=`transactionId`,
            notindexed=`categoryId`,
            prefix=`2,3,4`
        )
        """.trimIndent()
    )
}

private fun populateExpenseSearchIndex(connection: SQLiteConnection) {
    connection.execSQL(
        """
        INSERT INTO `expense_search_fts`(
            `transactionId`,
            `categoryId`,
            `categoryName`,
            `description`,
            `amountText`,
            `amountMinorText`,
            `dateText`,
            `localDateText`,
            `yearMonthText`
        )
        SELECT
            `expense`.`id`,
            `expense`.`categoryId`,
            COALESCE(`category`.`name`, ''),
            COALESCE(`expense`.`description`, ''),
            CASE WHEN `expense`.`amount` < 0 THEN '-' ELSE '' END ||
                CAST(ABS(`expense`.`amount`) / 100 AS TEXT) || '.' ||
                substr('00' || CAST(ABS(`expense`.`amount`) % 100 AS TEXT), -2, 2),
            CAST(`expense`.`amount` AS TEXT),
            CAST(`expense`.`localDate` / 10000 AS TEXT) || ' ' ||
                substr('00' || CAST((`expense`.`localDate` / 100) % 100 AS TEXT), -2, 2) || ' ' ||
                substr('00' || CAST(`expense`.`localDate` % 100 AS TEXT), -2, 2) || ' ' ||
                CAST(`expense`.`localDate` AS TEXT),
            CAST(`expense`.`localDate` AS TEXT),
            CAST(`expense`.`yearMonth` AS TEXT)
        FROM `expense`
        LEFT JOIN `category` ON `category`.`id` = `expense`.`categoryId`
        """.trimIndent()
    )
}

private fun populateIncomeSearchIndex(connection: SQLiteConnection) {
    connection.execSQL(
        """
        INSERT INTO `income_search_fts`(
            `transactionId`,
            `categoryId`,
            `categoryName`,
            `description`,
            `amountText`,
            `amountMinorText`,
            `dateText`,
            `localDateText`,
            `yearMonthText`
        )
        SELECT
            `income`.`id`,
            `income`.`categoryId`,
            COALESCE(`category`.`name`, ''),
            COALESCE(`income`.`description`, ''),
            CASE WHEN `income`.`amount` < 0 THEN '-' ELSE '' END ||
                CAST(ABS(`income`.`amount`) / 100 AS TEXT) || '.' ||
                substr('00' || CAST(ABS(`income`.`amount`) % 100 AS TEXT), -2, 2),
            CAST(`income`.`amount` AS TEXT),
            CAST(`income`.`localDate` / 10000 AS TEXT) || ' ' ||
                substr('00' || CAST((`income`.`localDate` / 100) % 100 AS TEXT), -2, 2) || ' ' ||
                substr('00' || CAST(`income`.`localDate` % 100 AS TEXT), -2, 2) || ' ' ||
                CAST(`income`.`localDate` AS TEXT),
            CAST(`income`.`localDate` AS TEXT),
            CAST(`income`.`yearMonth` AS TEXT)
        FROM `income`
        LEFT JOIN `category` ON `category`.`id` = `income`.`categoryId`
        """.trimIndent()
    )
}

private fun addStoredDateKeys(
    connection: SQLiteConnection,
    tableName: String,
    createIndexes: List<String>
) {
    connection.execSQL("ALTER TABLE `$tableName` ADD COLUMN `yearMonth` INTEGER NOT NULL DEFAULT 0")
    connection.execSQL("ALTER TABLE `$tableName` ADD COLUMN `localDate` INTEGER NOT NULL DEFAULT 0")
    connection.execSQL("ALTER TABLE `$tableName` ADD COLUMN `dayOfMonth` INTEGER NOT NULL DEFAULT 0")
    connection.execSQL(
        """
        UPDATE `$tableName`
        SET
            `yearMonth` =
                CAST(strftime('%Y', `date` / 1000, 'unixepoch', 'localtime') AS INTEGER) * 100 +
                CAST(strftime('%m', `date` / 1000, 'unixepoch', 'localtime') AS INTEGER),
            `localDate` =
                CAST(strftime('%Y', `date` / 1000, 'unixepoch', 'localtime') AS INTEGER) * 10000 +
                CAST(strftime('%m', `date` / 1000, 'unixepoch', 'localtime') AS INTEGER) * 100 +
                CAST(strftime('%d', `date` / 1000, 'unixepoch', 'localtime') AS INTEGER),
            `dayOfMonth` =
                CAST(strftime('%d', `date` / 1000, 'unixepoch', 'localtime') AS INTEGER)
        """
            .trimIndent()
    )
    createIndexes.forEach(connection::execSQL)
}

private fun migrateExpenseTable(connection: SQLiteConnection) {
    connection.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `expense_new` (
            `id` TEXT NOT NULL,
            `amount` INTEGER NOT NULL,
            `date` INTEGER NOT NULL,
            `categoryId` TEXT NOT NULL,
            `description` TEXT,
            `isShared` INTEGER NOT NULL DEFAULT 0,
            `recurringSeriesId` TEXT,
            PRIMARY KEY(`id`),
            FOREIGN KEY(`categoryId`) REFERENCES `category`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
        )
        """.trimIndent()
    )

    connection.prepare(
        """
        SELECT id, amount, date, categoryId, description, isShared, recurringSeriesId
        FROM expense
        """
    ).use { select ->
        connection.prepare(
            """
            INSERT INTO expense_new(id, amount, date, categoryId, description, isShared, recurringSeriesId)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """
        ).use { insert ->
            while (select.step()) {
                insert.bindText(1, select.getText(0))
                insert.bindLong(
                    2,
                    parseLegacyMinorAmount(
                        value = select.getText(1),
                        tableName = "expense",
                        rowId = select.getText(0)
                    )
                )
                insert.bindLong(3, select.getLong(2))
                insert.bindText(4, select.getText(3))
                bindNullableText(insert, 5, select, 4)
                insert.bindLong(6, select.getLong(5))
                bindNullableText(insert, 7, select, 6)
                insert.step()
                insert.reset()
            }
        }
    }

    connection.execSQL("DROP TABLE `expense`")
    connection.execSQL("ALTER TABLE `expense_new` RENAME TO `expense`")
    connection.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_categoryId` ON `expense` (`categoryId`)")
    connection.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_date` ON `expense` (`date`)")
    connection.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_expense_recurringSeriesId_date` ON `expense` (`recurringSeriesId`, `date`)"
    )
}

private fun migrateIncomeTable(connection: SQLiteConnection) {
    connection.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `income_new` (
            `id` TEXT NOT NULL,
            `amount` INTEGER NOT NULL,
            `date` INTEGER NOT NULL,
            `description` TEXT,
            `recurringSeriesId` TEXT,
            PRIMARY KEY(`id`)
        )
        """.trimIndent()
    )

    connection.prepare(
        """
        SELECT id, amount, date, description, recurringSeriesId
        FROM income
        """
    ).use { select ->
        connection.prepare(
            """
            INSERT INTO income_new(id, amount, date, description, recurringSeriesId)
            VALUES (?, ?, ?, ?, ?)
            """
        ).use { insert ->
            while (select.step()) {
                insert.bindText(1, select.getText(0))
                insert.bindLong(
                    2,
                    parseLegacyMinorAmount(
                        value = select.getText(1),
                        tableName = "income",
                        rowId = select.getText(0)
                    )
                )
                insert.bindLong(3, select.getLong(2))
                bindNullableText(insert, 4, select, 3)
                bindNullableText(insert, 5, select, 4)
                insert.step()
                insert.reset()
            }
        }
    }

    connection.execSQL("DROP TABLE `income`")
    connection.execSQL("ALTER TABLE `income_new` RENAME TO `income`")
    connection.execSQL("CREATE INDEX IF NOT EXISTS `index_income_date` ON `income` (`date`)")
    connection.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_income_recurringSeriesId_date` ON `income` (`recurringSeriesId`, `date`)"
    )
}

private fun bindNullableText(
    statement: SQLiteStatement,
    bindIndex: Int,
    select: SQLiteStatement,
    columnIndex: Int
) {
    if (select.isNull(columnIndex)) {
        statement.bindNull(bindIndex)
    } else {
        statement.bindText(bindIndex, select.getText(columnIndex))
    }
}

private fun parseLegacyMinorAmount(
    value: String,
    tableName: String,
    rowId: String
): Long {
    return value.trim().toLongOrNull()
        ?: error(
            "Cannot migrate $tableName row '$rowId': amount '$value' is not a valid Long minor-unit value."
        )
}
