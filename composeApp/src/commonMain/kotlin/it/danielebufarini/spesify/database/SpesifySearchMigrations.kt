package it.danielebufarini.spesify.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal fun createExpenseSearchTable(connection: SQLiteConnection) {
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

internal fun createIncomeSearchTable(connection: SQLiteConnection) {
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

internal fun populateExpenseSearchIndex(connection: SQLiteConnection) {
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

internal fun populateIncomeSearchIndex(connection: SQLiteConnection) {
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
