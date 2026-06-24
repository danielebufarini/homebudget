package it.danielebufarini.spesify.database

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.RawQuery
import androidx.room3.RoomRawQuery
import androidx.room3.Upsert
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

    @RawQuery(observedEntities = [Income::class, Category::class])
    fun searchIncomes(query: RoomRawQuery): Flow<List<Income>>

    @RawQuery(observedEntities = [Income::class, Category::class])
    fun searchIncomesAfter(query: RoomRawQuery): Flow<List<Income>>

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

internal fun incomeSearchQuery(
    ftsQuery: String,
    limit: Int,
    offset: Int
): RoomRawQuery = RoomRawQuery(
    sql = """
        SELECT income.*
        FROM income
        JOIN income_search_fts ON income_search_fts.transactionId = income.id
        WHERE income_search_fts MATCH ?
        ORDER BY income.date DESC, income.id ASC
        LIMIT ? OFFSET ?
    """.trimIndent()
) { statement ->
    statement.bindText(1, ftsQuery)
    statement.bindLong(2, limit.toLong())
    statement.bindLong(3, offset.toLong())
}

internal fun incomeSearchAfterQuery(
    ftsQuery: String,
    limit: Int,
    cursorDate: Long,
    cursorId: String
): RoomRawQuery = RoomRawQuery(
    sql = """
        SELECT income.*
        FROM income
        JOIN income_search_fts ON income_search_fts.transactionId = income.id
        WHERE income_search_fts MATCH ?
          AND (
            income.date < ?
            OR (income.date = ? AND income.id > ?)
          )
        ORDER BY income.date DESC, income.id ASC
        LIMIT ?
    """.trimIndent()
) { statement ->
    statement.bindText(1, ftsQuery)
    statement.bindLong(2, cursorDate)
    statement.bindLong(3, cursorDate)
    statement.bindText(4, cursorId)
    statement.bindLong(5, limit.toLong())
}
