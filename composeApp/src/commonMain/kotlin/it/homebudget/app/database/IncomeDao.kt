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
        SELECT * FROM income
        WHERE date < :toExclusiveMillis
        ORDER BY date DESC
        LIMIT :limit
        """
    )
    fun getRecentIncomes(
        limit: Int,
        toExclusiveMillis: Long
    ): Flow<List<Income>>

    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) AS totalAmount
        FROM income
        WHERE date >= :fromInclusiveMillis
          AND date < :toExclusiveMillis
        """
    )
    fun getIncomeAmountGroupBetween(
        fromInclusiveMillis: Long,
        toExclusiveMillis: Long
    ): Flow<DashboardTotalAmountRow>

    @Query(
        """
        SELECT
            CAST(strftime('%Y', date / 1000, 'unixepoch', 'localtime') AS INTEGER) AS year,
            CAST(strftime('%m', date / 1000, 'unixepoch', 'localtime') AS INTEGER) AS month,
            SUM(amount) AS totalAmount
        FROM income
        WHERE date >= :fromInclusiveMillis
          AND date < :toExclusiveMillis
        GROUP BY
            CAST(strftime('%Y', date / 1000, 'unixepoch', 'localtime') AS INTEGER),
            CAST(strftime('%m', date / 1000, 'unixepoch', 'localtime') AS INTEGER)
        ORDER BY year ASC, month ASC
        """
    )
    fun getDashboardIncomeMonthAmountGroupsBetween(
        fromInclusiveMillis: Long,
        toExclusiveMillis: Long
    ): Flow<List<DashboardMonthAmountGroupRow>>

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

    @Query("SELECT count(*) FROM income WHERE categoryId = :categoryId")
    suspend fun countIncomesForCategory(categoryId: String): Long

    @Query("UPDATE income SET categoryId = :newCategoryId WHERE categoryId = :oldCategoryId")
    suspend fun moveIncomesToCategory(oldCategoryId: String, newCategoryId: String)

    @Query("DELETE FROM income WHERE recurringSeriesId = :seriesId")
    suspend fun deleteRecurringIncomeSeries(seriesId: String)
}
