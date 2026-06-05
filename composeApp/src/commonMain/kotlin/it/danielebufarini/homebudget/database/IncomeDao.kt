package it.danielebufarini.homebudget.database

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
            yearMonth / 100 AS year,
            yearMonth % 100 AS month,
            SUM(amount) AS totalAmount
        FROM income
        WHERE yearMonth >= :fromInclusiveYearMonth
          AND yearMonth < :toExclusiveYearMonth
        GROUP BY yearMonth
        ORDER BY year ASC, month ASC
        """
    )
    fun getDashboardIncomeMonthAmountGroupsBetweenYearMonths(
        fromInclusiveYearMonth: Int,
        toExclusiveYearMonth: Int
    ): Flow<List<DashboardMonthAmountGroupRow>>

    @Query(
        """
        SELECT COALESCE(SUM(amount), 0)
        FROM income
        WHERE yearMonth < :toExclusiveYearMonth
        """
    )
    fun getDashboardIncomeTotalBeforeYearMonth(
        toExclusiveYearMonth: Int
    ): Flow<Long>


    @Query("SELECT * FROM income ORDER BY date DESC")
    suspend fun getAllIncomesSnapshot(): List<Income>

    @Query(
        """
        SELECT categoryId, COUNT(*) AS transactionCount
        FROM income
        WHERE categoryId IS NOT NULL
          AND categoryId != ''
        GROUP BY categoryId
        ORDER BY categoryId ASC
        """
    )
    fun getIncomeCategoryUsageCounts(): Flow<List<CategoryUsageCountRow>>

    @Query(
        """
        SELECT * FROM income
        WHERE date >= :startMillis
          AND date < :endMillis
        ORDER BY date DESC, id ASC
        """
    )
    fun getIncomesBetween(
        startMillis: Long,
        endMillis: Long
    ): Flow<List<Income>>

    @Query(
        """
        SELECT * FROM income
        WHERE date >= :startMillis
          AND date < :endMillis
        ORDER BY income.date DESC, income.id ASC
        LIMIT :limit OFFSET :offset
        """
    )
    fun getIncomesPageBetween(
        startMillis: Long,
        endMillis: Long,
        limit: Int,
        offset: Int
    ): Flow<List<Income>>

    @Query(
        """
        SELECT income.*
        FROM income
        JOIN income_search_fts ON income_search_fts.transactionId = income.id
        WHERE income_search_fts MATCH :ftsQuery
        ORDER BY income.date DESC, income.id ASC
        LIMIT :limit OFFSET :offset
        """
    )
    fun searchIncomes(
        ftsQuery: String,
        limit: Int,
        offset: Int
    ): Flow<List<Income>>

    @Query(
        """
        SELECT income.*
        FROM income
        JOIN income_search_fts ON income_search_fts.transactionId = income.id
        WHERE income_search_fts MATCH :ftsQuery
          AND (
            income.date < :cursorDate
            OR (income.date = :cursorDate AND income.id > :cursorId)
          )
        ORDER BY income.date DESC, income.id ASC
        LIMIT :limit
        """
    )
    fun searchIncomesAfter(
        ftsQuery: String,
        limit: Int,
        cursorDate: Long,
        cursorId: String
    ): Flow<List<Income>>

    @Query(
        """
        SELECT * FROM income
        WHERE date >= :startMillis
          AND date < :endMillis
        ORDER BY date DESC, id ASC
        """
    )
    suspend fun getIncomesSnapshotBetween(
        startMillis: Long,
        endMillis: Long
    ): List<Income>

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

    @Query("SELECT id FROM income WHERE categoryId = :categoryId")
    suspend fun getIncomeIdsForCategory(categoryId: String): List<String>

    @Query("UPDATE income SET categoryId = :newCategoryId WHERE categoryId = :oldCategoryId")
    suspend fun moveIncomesToCategory(oldCategoryId: String, newCategoryId: String)

    @Query("DELETE FROM income WHERE recurringSeriesId = :seriesId")
    suspend fun deleteRecurringIncomeSeries(seriesId: String)
}
