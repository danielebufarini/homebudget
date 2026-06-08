package it.danielebufarini.spesify.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface RecurringTransactionRuleDao {
    @Query("SELECT * FROM recurring_transaction_rule ORDER BY startDate ASC, id ASC")
    suspend fun getAllRulesSnapshot(): List<RecurringTransactionRule>

    @Query("SELECT * FROM recurring_transaction_rule WHERE id = :id")
    suspend fun getRuleById(id: String): RecurringTransactionRule?

    @Query(
        """
        SELECT * FROM recurring_transaction_rule
        WHERE frequency = 'monthly'
          AND generatedThroughYearMonth < :targetYearMonth
        ORDER BY startDate ASC, id ASC
        """
    )
    suspend fun getMonthlyRulesGeneratedBefore(targetYearMonth: Int): List<RecurringTransactionRule>

    @Upsert
    suspend fun upsertRule(rule: RecurringTransactionRule)

    @Upsert
    suspend fun upsertRules(rules: List<RecurringTransactionRule>)

    @Query("UPDATE recurring_transaction_rule SET generatedThroughYearMonth = :yearMonth WHERE id = :id")
    suspend fun updateGeneratedThroughYearMonth(id: String, yearMonth: Int)

    @Query("UPDATE recurring_transaction_rule SET categoryId = :newCategoryId WHERE categoryId = :oldCategoryId")
    suspend fun moveRulesToCategory(oldCategoryId: String, newCategoryId: String)

    @Query("SELECT count(*) FROM recurring_transaction_rule WHERE categoryId = :categoryId")
    suspend fun countRulesForCategory(categoryId: String): Long

    @Query("SELECT count(*) FROM recurring_transaction_rule WHERE categoryId = :categoryId AND kind = 'expense'")
    suspend fun countExpenseRulesForCategory(categoryId: String): Long

    @Query("SELECT count(*) FROM recurring_transaction_rule WHERE categoryId = :categoryId AND kind = 'income'")
    suspend fun countIncomeRulesForCategory(categoryId: String): Long

    @Query("DELETE FROM recurring_transaction_rule WHERE id = :id")
    suspend fun deleteRule(id: String)

    @Query("DELETE FROM recurring_transaction_rule")
    suspend fun deleteAllRules()
}
