package it.homebudget.app.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expense ORDER BY date DESC")
    fun getAllExpenses(): Flow<List<Expense>>

    @Query(
        """
        SELECT
            COUNT(*) AS expenseCount,
            COALESCE(SUM(CAST(amount AS INTEGER)), 0) AS totalAmount,
            COALESCE(SUM(CASE WHEN isShared = 1 THEN CAST(amount AS INTEGER) ELSE 0 END), 0) AS sharedAmount
        FROM expense
        WHERE CAST(strftime('%Y', date / 1000, 'unixepoch', 'localtime') AS INTEGER) = :year
          AND CAST(strftime('%m', date / 1000, 'unixepoch', 'localtime') AS INTEGER) = :month
        """
    )
    fun getExpenseMonthSummary(year: Int, month: Int): Flow<ExpenseMonthSummaryRow>

    @Query(
        """
        SELECT
            CAST(strftime('%Y', date / 1000, 'unixepoch', 'localtime') AS INTEGER) AS year,
            CAST(strftime('%m', date / 1000, 'unixepoch', 'localtime') AS INTEGER) AS month,
            COALESCE(SUM(CAST(amount AS INTEGER)), 0) AS amount
        FROM expense
        GROUP BY
            CAST(strftime('%Y', date / 1000, 'unixepoch', 'localtime') AS INTEGER),
            CAST(strftime('%m', date / 1000, 'unixepoch', 'localtime') AS INTEGER)
        ORDER BY year, month
        """
    )
    fun getMonthlyExpenseTotals(): Flow<List<MonthTotalRow>>

    @Query(
        """
        SELECT
            categoryId AS categoryId,
            COALESCE(SUM(CAST(amount AS INTEGER)), 0) AS amount
        FROM expense
        WHERE CAST(strftime('%Y', date / 1000, 'unixepoch', 'localtime') AS INTEGER) = :year
          AND CAST(strftime('%m', date / 1000, 'unixepoch', 'localtime') AS INTEGER) = :month
        GROUP BY categoryId
        ORDER BY amount DESC, MAX(date) DESC
        """
    )
    fun getMonthCategoryTotals(year: Int, month: Int): Flow<List<CategoryTotalRow>>

    @Query(
        """
        SELECT
            categoryId AS categoryId,
            COALESCE(SUM(CAST(amount AS INTEGER)), 0) AS amount
        FROM expense
        WHERE CAST(strftime('%Y', date / 1000, 'unixepoch', 'localtime') AS INTEGER) = :year
          AND CAST(strftime('%m', date / 1000, 'unixepoch', 'localtime') AS INTEGER) = :month
        GROUP BY categoryId
        ORDER BY amount DESC, MAX(date) DESC
        LIMIT 1
        """
    )
    fun getMonthTopCategory(year: Int, month: Int): Flow<TopCategorySummaryRow?>

    @Query(
        """
        SELECT
            CAST(strftime('%d', date / 1000, 'unixepoch', 'localtime') AS INTEGER) AS dayOfMonth,
            COALESCE(SUM(CAST(amount AS INTEGER)), 0) AS amount
        FROM expense
        WHERE CAST(strftime('%Y', date / 1000, 'unixepoch', 'localtime') AS INTEGER) = :year
          AND CAST(strftime('%m', date / 1000, 'unixepoch', 'localtime') AS INTEGER) = :month
        GROUP BY dayOfMonth
        ORDER BY amount DESC, MAX(date) DESC
        LIMIT 1
        """
    )
    fun getMonthHighestDay(year: Int, month: Int): Flow<HighestDaySummaryRow?>

    @Query("SELECT * FROM expense ORDER BY date DESC")
    suspend fun getAllExpensesSnapshot(): List<Expense>

    @Query("SELECT * FROM expense WHERE id = :id")
    suspend fun getExpenseById(id: String): Expense?

    @Query("SELECT * FROM expense WHERE recurringSeriesId = :seriesId ORDER BY date ASC")
    suspend fun getRecurringExpensesBySeries(seriesId: String): List<Expense>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense)

    @Query("DELETE FROM expense WHERE id = :id")
    suspend fun deleteExpense(id: String)

    @Query("DELETE FROM expense")
    suspend fun deleteAllExpenses()

    @Query("DELETE FROM expense WHERE recurringSeriesId = :seriesId")
    suspend fun deleteRecurringExpenseSeries(seriesId: String)
}
