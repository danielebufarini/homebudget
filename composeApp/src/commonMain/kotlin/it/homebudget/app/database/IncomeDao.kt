package it.homebudget.app.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface IncomeDao {
    @Query("SELECT * FROM income ORDER BY date DESC")
    fun getAllIncomes(): Flow<List<Income>>

    @Query(
        """
    SELECT
        amount,
        date
    FROM income
    WHERE date >= :fromInclusiveMillis
      AND date < :toExclusiveMillis
    ORDER BY date ASC
    """
    )
    fun getIncomeAmountRowsBetween(
        fromInclusiveMillis: Long,
        toExclusiveMillis: Long
    ): Flow<List<IncomeAmountRow>>

    @Query("SELECT * FROM income ORDER BY date DESC")
    suspend fun getAllIncomesSnapshot(): List<Income>

    @Query(
        """
        SELECT * FROM income
        WHERE date >= :startMillis
          AND date < :endMillis
        ORDER BY date DESC
        """
    )
    fun getIncomesBetween(
        startMillis: Long,
        endMillis: Long
    ): Flow<List<Income>>

    @Query("SELECT * FROM income WHERE id = :id")
    suspend fun getIncomeById(id: String): Income?

    @Query("SELECT * FROM income WHERE recurringSeriesId = :seriesId ORDER BY date ASC")
    suspend fun getRecurringIncomesBySeries(seriesId: String): List<Income>

    @Upsert
    suspend fun insertIncome(income: Income)

    @Upsert
    suspend fun insertIncomes(incomes: List<Income>)

    @Query("DELETE FROM income WHERE id = :id")
    suspend fun deleteIncome(id: String)

    @Query("DELETE FROM income")
    suspend fun deleteAllIncomes()

    @Query("DELETE FROM income WHERE recurringSeriesId = :seriesId")
    suspend fun deleteRecurringIncomeSeries(seriesId: String)
}
