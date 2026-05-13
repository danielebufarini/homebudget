package it.homebudget.app.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Query(
        """
        SELECT COUNT(*)
        FROM expense
        WHERE date >= :fromInclusiveMillis
          AND date < :toExclusiveMillis
        """
    )
    fun getExpenseCountBetween(
        fromInclusiveMillis: Long,
        toExclusiveMillis: Long
    ): Flow<Int>

    @Query("SELECT * FROM expense ORDER BY date DESC")
    fun getAllExpenses(): Flow<List<Expense>>

    @Query(
        """
    SELECT
        categoryId,
        MAX(date) AS latestExpenseDate,
        SUM(amount) AS totalAmount
    FROM expense
    WHERE date >= :fromInclusiveMillis
      AND date < :toExclusiveMillis
    GROUP BY categoryId
    ORDER BY categoryId ASC
    """
    )
    fun getDashboardCategoryAmountGroupsBetween(
        fromInclusiveMillis: Long,
        toExclusiveMillis: Long
    ): Flow<List<DashboardCategoryAmountGroupRow>>

    @Query(
        """
        SELECT
            date,
            SUM(amount) AS totalAmount
        FROM expense
        WHERE date >= :fromInclusiveMillis
          AND date < :toExclusiveMillis
        GROUP BY date
        ORDER BY date ASC
        """
    )
    fun getDashboardDayAmountGroupsBetween(
        fromInclusiveMillis: Long,
        toExclusiveMillis: Long
    ): Flow<List<DashboardDayAmountGroupRow>>

    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) AS totalAmount
        FROM expense
        WHERE date >= :fromInclusiveMillis
          AND date < :toExclusiveMillis
          AND isShared = 1
        """
    )
    fun getSharedExpenseAmountGroupBetween(
        fromInclusiveMillis: Long,
        toExclusiveMillis: Long
    ): Flow<DashboardTotalAmountRow>

    @Query(
        """
        SELECT
            CAST(strftime('%Y', date / 1000, 'unixepoch', 'localtime') AS INTEGER) AS year,
            CAST(strftime('%m', date / 1000, 'unixepoch', 'localtime') AS INTEGER) AS month,
            SUM(amount) AS totalAmount
        FROM expense
        WHERE date >= :fromInclusiveMillis
          AND date < :toExclusiveMillis
        GROUP BY
            CAST(strftime('%Y', date / 1000, 'unixepoch', 'localtime') AS INTEGER),
            CAST(strftime('%m', date / 1000, 'unixepoch', 'localtime') AS INTEGER)
        ORDER BY year ASC, month ASC
        """
    )
    fun getDashboardExpenseMonthAmountGroupsBetween(
        fromInclusiveMillis: Long,
        toExclusiveMillis: Long
    ): Flow<List<DashboardMonthAmountGroupRow>>

    @Query("SELECT * FROM expense ORDER BY date DESC")
    suspend fun getAllExpensesSnapshot(): List<Expense>

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
