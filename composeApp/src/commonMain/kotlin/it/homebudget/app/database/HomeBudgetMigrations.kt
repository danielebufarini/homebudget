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

internal fun RoomDatabase.Builder<HomeBudgetDatabase>.addHomeBudgetMigrations():
    RoomDatabase.Builder<HomeBudgetDatabase> = addMigrations(MIGRATION_1_2, MIGRATION_2_3)

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
