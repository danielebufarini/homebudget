package it.danielebufarini.homebudget.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal fun migrateIncomeTableForCategories(connection: SQLiteConnection) {
    connection.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `income_new` (
            `id` TEXT NOT NULL,
            `amount` INTEGER NOT NULL,
            `date` INTEGER NOT NULL,
            `description` TEXT,
            `recurringSeriesId` TEXT,
            `categoryId` TEXT DEFAULT NULL,
            PRIMARY KEY(`id`),
            FOREIGN KEY(`categoryId`) REFERENCES `category`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
        )
        """.trimIndent()
    )

    connection.execSQL(
        """
        INSERT INTO `income_new`(
            `id`,
            `amount`,
            `date`,
            `description`,
            `recurringSeriesId`,
            `categoryId`
        )
        SELECT
            `id`,
            `amount`,
            `date`,
            `description`,
            `recurringSeriesId`,
            NULL
        FROM `income`
        """.trimIndent()
    )

    connection.execSQL("DROP TABLE `income`")
    connection.execSQL("ALTER TABLE `income_new` RENAME TO `income`")
    connection.execSQL("CREATE INDEX IF NOT EXISTS `index_income_categoryId` ON `income` (`categoryId`)")
    connection.execSQL("CREATE INDEX IF NOT EXISTS `index_income_date` ON `income` (`date`)")
    connection.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_income_recurringSeriesId_date` ON `income` (`recurringSeriesId`, `date`)"
    )
}

internal fun migrateCategoryTableForSortOrder(connection: SQLiteConnection) {
    connection.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `category_new` (
            `id` TEXT NOT NULL,
            `name` TEXT NOT NULL,
            `icon` TEXT NOT NULL,
            `color` TEXT NOT NULL DEFAULT '#6F45E9',
            `categoryType` TEXT NOT NULL DEFAULT 'expense',
            `isArchived` INTEGER NOT NULL DEFAULT 0,
            `sortOrder` INTEGER NOT NULL DEFAULT 0,
            PRIMARY KEY(`id`)
        )
        """.trimIndent()
    )

    connection.execSQL(
        """
        INSERT INTO `category_new`(
            `id`,
            `name`,
            `icon`,
            `color`,
            `categoryType`,
            `isArchived`,
            `sortOrder`
        )
        SELECT
            `id`,
            `name`,
            `icon`,
            `color`,
            `categoryType`,
            `isArchived`,
            0
        FROM `category`
        """.trimIndent()
    )

    migrateExpenseTableForCategorySchemaRefresh(connection)
    migrateIncomeTableForCategorySchemaRefresh(connection)

    connection.execSQL("DROP TABLE `category`")
    connection.execSQL("ALTER TABLE `category_new` RENAME TO `category`")
}

internal fun migrateExpenseTableForCategorySchemaRefresh(connection: SQLiteConnection) {
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
            FOREIGN KEY(`categoryId`) REFERENCES `category_new`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
        )
        """.trimIndent()
    )

    connection.execSQL(
        """
        INSERT INTO `expense_new`(
            `id`,
            `amount`,
            `date`,
            `categoryId`,
            `description`,
            `isShared`,
            `recurringSeriesId`
        )
        SELECT
            `id`,
            `amount`,
            `date`,
            `categoryId`,
            `description`,
            `isShared`,
            `recurringSeriesId`
        FROM `expense`
        """.trimIndent()
    )

    connection.execSQL("DROP TABLE `expense`")
    connection.execSQL("ALTER TABLE `expense_new` RENAME TO `expense`")
    connection.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_categoryId` ON `expense` (`categoryId`)")
    connection.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_date` ON `expense` (`date`)")
    connection.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_expense_recurringSeriesId_date` ON `expense` (`recurringSeriesId`, `date`)"
    )
}

internal fun migrateIncomeTableForCategorySchemaRefresh(connection: SQLiteConnection) {
    connection.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `income_new` (
            `id` TEXT NOT NULL,
            `amount` INTEGER NOT NULL,
            `date` INTEGER NOT NULL,
            `description` TEXT,
            `recurringSeriesId` TEXT,
            `categoryId` TEXT DEFAULT NULL,
            PRIMARY KEY(`id`),
            FOREIGN KEY(`categoryId`) REFERENCES `category_new`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
        )
        """.trimIndent()
    )

    connection.execSQL(
        """
        INSERT INTO `income_new`(
            `id`,
            `amount`,
            `date`,
            `description`,
            `recurringSeriesId`,
            `categoryId`
        )
        SELECT
            `id`,
            `amount`,
            `date`,
            `description`,
            `recurringSeriesId`,
            `categoryId`
        FROM `income`
        """.trimIndent()
    )

    connection.execSQL("DROP TABLE `income`")
    connection.execSQL("ALTER TABLE `income_new` RENAME TO `income`")
    connection.execSQL("CREATE INDEX IF NOT EXISTS `index_income_categoryId` ON `income` (`categoryId`)")
    connection.execSQL("CREATE INDEX IF NOT EXISTS `index_income_date` ON `income` (`date`)")
    connection.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_income_recurringSeriesId_date` ON `income` (`recurringSeriesId`, `date`)"
    )
}
