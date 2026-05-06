package it.homebudget.app.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface IncomeDao {
    @Query("SELECT * FROM income ORDER BY date DESC")
    fun getAllIncomes(): Flow<List<Income>>

    @Query(
        """
        SELECT COALESCE(SUM(CAST(amount AS INTEGER)), 0)
        FROM income
        WHERE CAST(strftime('%Y', date / 1000, 'unixepoch', 'localtime') AS INTEGER) = :year
          AND CAST(strftime('%m', date / 1000, 'unixepoch', 'localtime') AS INTEGER) = :month
        """
    )
    fun getIncomeMonthTotal(year: Int, month: Int): Flow<Long>

    @Query(
        """
        SELECT
            CAST(strftime('%Y', date / 1000, 'unixepoch', 'localtime') AS INTEGER) AS year,
            CAST(strftime('%m', date / 1000, 'unixepoch', 'localtime') AS INTEGER) AS month,
            COALESCE(SUM(CAST(amount AS INTEGER)), 0) AS amount
        FROM income
        GROUP BY
            CAST(strftime('%Y', date / 1000, 'unixepoch', 'localtime') AS INTEGER),
            CAST(strftime('%m', date / 1000, 'unixepoch', 'localtime') AS INTEGER)
        ORDER BY year, month
        """
    )
    fun getMonthlyIncomeTotals(): Flow<List<MonthTotalRow>>

    @Query("SELECT * FROM income ORDER BY date DESC")
    suspend fun getAllIncomesSnapshot(): List<Income>

    @Query("SELECT * FROM income WHERE id = :id")
    suspend fun getIncomeById(id: String): Income?

    @Query("SELECT * FROM income WHERE recurringSeriesId = :seriesId ORDER BY date ASC")
    suspend fun getRecurringIncomesBySeries(seriesId: String): List<Income>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIncome(income: Income)

    @Query("DELETE FROM income WHERE id = :id")
    suspend fun deleteIncome(id: String)

    @Query("DELETE FROM income")
    suspend fun deleteAllIncomes()

    @Query("DELETE FROM income WHERE recurringSeriesId = :seriesId")
    suspend fun deleteRecurringIncomeSeries(seriesId: String)
}
