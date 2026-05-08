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
    SELECT
        amount,
        isShared,
        date,
        categoryId
    FROM expense
    WHERE date >= :fromInclusiveMillis
      AND date < :toExclusiveMillis
    ORDER BY date ASC
    """
    )
    fun getDashboardExpenseRowsBetween(
        fromInclusiveMillis: Long,
        toExclusiveMillis: Long
    ): Flow<List<DashboardExpenseRow>>

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

    @Query("DELETE FROM expense WHERE recurringSeriesId = :seriesId")
    suspend fun deleteRecurringExpenseSeries(seriesId: String)
}
