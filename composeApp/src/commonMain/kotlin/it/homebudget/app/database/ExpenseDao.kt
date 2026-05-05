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

    @Query(
        """
        DELETE FROM expense
        WHERE recurringSeriesId = :seriesId
          AND date >= :fromDate
        """
    )
    suspend fun deleteRecurringExpensesFrom(seriesId: String, fromDate: Long)

    @Query(
        """
        UPDATE expense
        SET isShared = :isShared
        WHERE recurringSeriesId = :seriesId
        """
    )
    suspend fun updateRecurringExpenseShared(seriesId: String, isShared: Long)
}
