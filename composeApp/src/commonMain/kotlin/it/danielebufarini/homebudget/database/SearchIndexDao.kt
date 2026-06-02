package it.danielebufarini.homebudget.database

import androidx.room.Dao
import androidx.room.Query

@Dao
interface SearchIndexDao {

    @Query("DELETE FROM expense_search_fts")
    suspend fun clearExpenseSearchIndex()

    @Query("DELETE FROM income_search_fts")
    suspend fun clearIncomeSearchIndex()

    @Query("DELETE FROM expense_search_fts WHERE transactionId IN (:ids)")
    suspend fun deleteExpenseSearchRows(ids: List<String>)

    @Query("DELETE FROM income_search_fts WHERE transactionId IN (:ids)")
    suspend fun deleteIncomeSearchRows(ids: List<String>)

    @Query(
        """
        INSERT INTO expense_search_fts(
            transactionId,
            categoryId,
            categoryName,
            description,
            amountText,
            amountMinorText,
            dateText,
            localDateText,
            yearMonthText
        )
        SELECT
            expense.id,
            expense.categoryId,
            COALESCE(category.name, ''),
            COALESCE(expense.description, ''),
            CASE WHEN expense.amount < 0 THEN '-' ELSE '' END ||
                CAST(ABS(expense.amount) / 100 AS TEXT) || '.' ||
                substr('00' || CAST(ABS(expense.amount) % 100 AS TEXT), -2, 2),
            CAST(expense.amount AS TEXT),
            CAST(expense.localDate / 10000 AS TEXT) || ' ' ||
                substr('00' || CAST((expense.localDate / 100) % 100 AS TEXT), -2, 2) || ' ' ||
                substr('00' || CAST(expense.localDate % 100 AS TEXT), -2, 2) || ' ' ||
                CAST(expense.localDate AS TEXT),
            CAST(expense.localDate AS TEXT),
            CAST(expense.yearMonth AS TEXT)
        FROM expense
        LEFT JOIN category ON category.id = expense.categoryId
        """
    )
    suspend fun populateExpenseSearchIndex()

    @Query(
        """
        INSERT INTO expense_search_fts(
            transactionId,
            categoryId,
            categoryName,
            description,
            amountText,
            amountMinorText,
            dateText,
            localDateText,
            yearMonthText
        )
        SELECT
            expense.id,
            expense.categoryId,
            COALESCE(category.name, ''),
            COALESCE(expense.description, ''),
            CASE WHEN expense.amount < 0 THEN '-' ELSE '' END ||
                CAST(ABS(expense.amount) / 100 AS TEXT) || '.' ||
                substr('00' || CAST(ABS(expense.amount) % 100 AS TEXT), -2, 2),
            CAST(expense.amount AS TEXT),
            CAST(expense.localDate / 10000 AS TEXT) || ' ' ||
                substr('00' || CAST((expense.localDate / 100) % 100 AS TEXT), -2, 2) || ' ' ||
                substr('00' || CAST(expense.localDate % 100 AS TEXT), -2, 2) || ' ' ||
                CAST(expense.localDate AS TEXT),
            CAST(expense.localDate AS TEXT),
            CAST(expense.yearMonth AS TEXT)
        FROM expense
        LEFT JOIN category ON category.id = expense.categoryId
        WHERE expense.id IN (:ids)
        """
    )
    suspend fun populateExpenseSearchRows(ids: List<String>)

    @Query(
        """
        INSERT INTO income_search_fts(
            transactionId,
            categoryId,
            categoryName,
            description,
            amountText,
            amountMinorText,
            dateText,
            localDateText,
            yearMonthText
        )
        SELECT
            income.id,
            income.categoryId,
            COALESCE(category.name, ''),
            COALESCE(income.description, ''),
            CASE WHEN income.amount < 0 THEN '-' ELSE '' END ||
                CAST(ABS(income.amount) / 100 AS TEXT) || '.' ||
                substr('00' || CAST(ABS(income.amount) % 100 AS TEXT), -2, 2),
            CAST(income.amount AS TEXT),
            CAST(income.localDate / 10000 AS TEXT) || ' ' ||
                substr('00' || CAST((income.localDate / 100) % 100 AS TEXT), -2, 2) || ' ' ||
                substr('00' || CAST(income.localDate % 100 AS TEXT), -2, 2) || ' ' ||
                CAST(income.localDate AS TEXT),
            CAST(income.localDate AS TEXT),
            CAST(income.yearMonth AS TEXT)
        FROM income
        LEFT JOIN category ON category.id = income.categoryId
        """
    )
    suspend fun populateIncomeSearchIndex()

    @Query(
        """
        INSERT INTO income_search_fts(
            transactionId,
            categoryId,
            categoryName,
            description,
            amountText,
            amountMinorText,
            dateText,
            localDateText,
            yearMonthText
        )
        SELECT
            income.id,
            income.categoryId,
            COALESCE(category.name, ''),
            COALESCE(income.description, ''),
            CASE WHEN income.amount < 0 THEN '-' ELSE '' END ||
                CAST(ABS(income.amount) / 100 AS TEXT) || '.' ||
                substr('00' || CAST(ABS(income.amount) % 100 AS TEXT), -2, 2),
            CAST(income.amount AS TEXT),
            CAST(income.localDate / 10000 AS TEXT) || ' ' ||
                substr('00' || CAST((income.localDate / 100) % 100 AS TEXT), -2, 2) || ' ' ||
                substr('00' || CAST(income.localDate % 100 AS TEXT), -2, 2) || ' ' ||
                CAST(income.localDate AS TEXT),
            CAST(income.localDate AS TEXT),
            CAST(income.yearMonth AS TEXT)
        FROM income
        LEFT JOIN category ON category.id = income.categoryId
        WHERE income.id IN (:ids)
        """
    )
    suspend fun populateIncomeSearchRows(ids: List<String>)
}

suspend fun SearchIndexDao.refreshExpenseSearchRows(ids: List<String>) {
    if (ids.isEmpty()) return
    deleteExpenseSearchRows(ids)
    populateExpenseSearchRows(ids)
}

suspend fun SearchIndexDao.refreshIncomeSearchRows(ids: List<String>) {
    if (ids.isEmpty()) return
    deleteIncomeSearchRows(ids)
    populateIncomeSearchRows(ids)
}

suspend fun SearchIndexDao.rebuildExpenseSearchIndex() {
    clearExpenseSearchIndex()
    populateExpenseSearchIndex()
}

suspend fun SearchIndexDao.rebuildIncomeSearchIndex() {
    clearIncomeSearchIndex()
    populateIncomeSearchIndex()
}

suspend fun SearchIndexDao.rebuildAllSearchIndexes() {
    rebuildExpenseSearchIndex()
    rebuildIncomeSearchIndex()
}
