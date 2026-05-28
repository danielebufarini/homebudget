package it.homebudget.app.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Query("SELECT * FROM expense ORDER BY date DESC")
    fun getAllExpenses(): Flow<List<Expense>>

    @Query(
        """
        SELECT id, 0 AS typeOrdinal, amount, date, categoryId, description
        FROM expense
        WHERE date < :toExclusiveMillis
        UNION ALL
        SELECT id, 1 AS typeOrdinal, amount, date, categoryId, description
        FROM income
        WHERE date < :toExclusiveMillis
        ORDER BY date DESC, typeOrdinal ASC, id ASC
        LIMIT :limit
        """
    )
    fun getRecentTransactions(
        limit: Int,
        toExclusiveMillis: Long
    ): Flow<List<DashboardRecentTransactionRow>>

    @Query(
        """
    SELECT
        categoryId,
        MAX(date) AS latestExpenseDate,
        SUM(amount) AS totalAmount
    FROM expense
    WHERE yearMonth = :yearMonth
    GROUP BY categoryId
    ORDER BY categoryId ASC
    """
    )
    fun getDashboardCategoryAmountGroupsForYearMonth(
        yearMonth: Int
    ): Flow<List<DashboardCategoryAmountGroupRow>>

    @Query(
        """
        SELECT
            (
                SELECT COUNT(*)
                FROM expense
                WHERE yearMonth = :yearMonth
            ) AS expenseCount,
            (
                SELECT COALESCE(SUM(amount), 0)
                FROM expense
                WHERE yearMonth = :yearMonth
            ) AS totalAmount,
            (
                SELECT COALESCE(SUM(amount), 0)
                FROM expense
                WHERE yearMonth = :yearMonth
                  AND isShared = 1
            ) AS sharedAmount,
            (
                SELECT COALESCE(SUM(amount), 0)
                FROM income
                WHERE yearMonth = :yearMonth
            ) AS incomeAmount
        """
    )
    fun getDashboardMonthSummaryAmountsForYearMonth(
        yearMonth: Int
    ): Flow<DashboardMonthSummaryAmountRow>

    @Query(
        """
        SELECT
            dayOfMonth,
            SUM(amount) AS amount
        FROM expense
        WHERE yearMonth = :yearMonth
        GROUP BY localDate, dayOfMonth
        ORDER BY amount DESC, localDate DESC
        LIMIT 1
        """
    )
    fun getDashboardHighestDayForYearMonth(
        yearMonth: Int
    ): Flow<HighestDaySummaryRow?>

    @Query(
        """
        SELECT
            yearMonth / 100 AS year,
            yearMonth % 100 AS month,
            SUM(amount) AS totalAmount
        FROM expense
        WHERE yearMonth >= :fromInclusiveYearMonth
          AND yearMonth < :toExclusiveYearMonth
        GROUP BY yearMonth
        ORDER BY year ASC, month ASC
        """
    )
    fun getDashboardExpenseMonthAmountGroupsBetweenYearMonths(
        fromInclusiveYearMonth: Int,
        toExclusiveYearMonth: Int
    ): Flow<List<DashboardMonthAmountGroupRow>>

    @Query(
        """
        SELECT COALESCE(SUM(amount), 0)
        FROM expense
        WHERE yearMonth < :toExclusiveYearMonth
        """
    )
    fun getDashboardExpenseTotalBeforeYearMonth(
        toExclusiveYearMonth: Int
    ): Flow<Long>


    @Query(
        """
        SELECT
            (
                SELECT COALESCE(SUM(amount), 0)
                FROM expense
                WHERE yearMonth = :yearMonth
            ) AS expenseAmount,
            (
                SELECT COALESCE(SUM(amount), 0)
                FROM income
                WHERE yearMonth = :yearMonth
            ) AS incomeAmount
        """
    )
    suspend fun getWidgetMonthSummaryForYearMonth(
        yearMonth: Int
    ): WidgetMonthSummaryRow

    @Query("SELECT * FROM expense ORDER BY date DESC")
    suspend fun getAllExpensesSnapshot(): List<Expense>

    @Query("SELECT * FROM expense ORDER BY date DESC LIMIT :limit")
    suspend fun getRecentExpensesSnapshot(limit: Int): List<Expense>

    @Query(
        """
        SELECT categoryId, COUNT(*) AS transactionCount
        FROM expense
        GROUP BY categoryId
        ORDER BY categoryId ASC
        """
    )
    fun getExpenseCategoryUsageCounts(): Flow<List<CategoryUsageCountRow>>

    @Query(
        """
        SELECT * FROM expense
        WHERE date >= :startMillis
          AND date < :endMillis
        ORDER BY date DESC
        """
    )
    fun getExpensesBetween(
        startMillis: Long,
        endMillis: Long
    ): Flow<List<Expense>>

    @Query(
        """
        SELECT * FROM expense
        WHERE date >= :startMillis
          AND date < :endMillis
        ORDER BY date DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun getExpensesPageBetween(
        startMillis: Long,
        endMillis: Long,
        limit: Int,
        offset: Int
    ): Flow<List<Expense>>

    @Query(
        """
        SELECT * FROM expense
        WHERE date >= :startMillis
          AND date < :endMillis
        ORDER BY date DESC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getExpensesPageSnapshotBetween(
        startMillis: Long,
        endMillis: Long,
        limit: Int,
        offset: Int
    ): List<Expense>

    @Query(
        """
        SELECT expense.*
        FROM expense
        JOIN expense_search_fts ON expense_search_fts.transactionId = expense.id
        WHERE expense_search_fts MATCH :ftsQuery
        ORDER BY expense.date DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun searchExpenses(
        ftsQuery: String,
        limit: Int,
        offset: Int
    ): Flow<List<Expense>>

    @Query(
        """
        SELECT * FROM expense
        WHERE id IN (:ids)
        ORDER BY date DESC
        """
    )
    fun getExpensesByIds(ids: List<String>): Flow<List<Expense>>

    @Query(
        """
        SELECT * FROM expense
        WHERE date >= :startMillis
          AND date < :endMillis
        ORDER BY date DESC
        """
    )
    suspend fun getExpensesSnapshotBetween(
        startMillis: Long,
        endMillis: Long
    ): List<Expense>

    @Query("SELECT * FROM expense WHERE id = :id")
    suspend fun getExpenseById(id: String): Expense?

    @Query("SELECT * FROM expense WHERE recurringSeriesId = :seriesId ORDER BY date ASC")
    suspend fun getRecurringExpensesBySeries(seriesId: String): List<Expense>

    @Upsert
    suspend fun insertExpense(expense: Expense)

    @Upsert
    suspend fun insertExpenses(expenses: List<Expense>)

    @Query("DELETE FROM expense WHERE id = :id")
    suspend fun deleteExpense(id: String)

    @Query("DELETE FROM expense")
    suspend fun deleteAllExpenses()

    @Query("SELECT count(*) FROM expense WHERE categoryId = :categoryId")
    suspend fun countExpensesForCategory(categoryId: String): Long

    @Query("UPDATE expense SET categoryId = :newCategoryId WHERE categoryId = :oldCategoryId")
    suspend fun moveExpensesToCategory(oldCategoryId: String, newCategoryId: String)

    @Query("DELETE FROM expense WHERE recurringSeriesId = :seriesId")
    suspend fun deleteRecurringExpenseSeries(seriesId: String)
}
