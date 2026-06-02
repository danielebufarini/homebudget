package it.danielebufarini.homebudget.data

import it.danielebufarini.homebudget.database.Category
import it.danielebufarini.homebudget.database.CategoryUsageCountRow
import it.danielebufarini.homebudget.database.Expense
import it.danielebufarini.homebudget.database.Income
import kotlinx.coroutines.flow.Flow

internal interface CategoryManagementRepository {
    fun getAllCategories(): Flow<List<Category>>

    suspend fun getAllCategoriesSnapshot(): List<Category>

    suspend fun insertCategory(
        id: String,
        name: String,
        icon: String,
        color: String,
        categoryType: String,
        isArchived: Boolean,
        sortOrder: Long?
    )

    suspend fun updateCategory(
        id: String,
        name: String,
        icon: String,
        color: String?,
        categoryType: String?
    )

    suspend fun seedStarterCategoriesIfEmpty()

    suspend fun deleteCategory(id: String)

    suspend fun isCategoryInUse(id: String): Boolean

    suspend fun setCategoryArchived(id: String, isArchived: Boolean)

    suspend fun updateCategorySortOrder(id: String, sortOrder: Long)

    suspend fun reassignCategoryTransactions(sourceCategoryId: String, targetCategoryId: String)
}

internal interface ExpenseReadRepository {
    fun getAllExpenses(): Flow<List<Expense>>

    fun getExpenseCategoryUsageCounts(): Flow<List<CategoryUsageCountRow>>

    fun getExpensesBetween(startMillis: Long, endMillis: Long): Flow<List<Expense>>

    fun getExpensesPageBetween(
        startMillis: Long,
        endMillis: Long,
        limit: Int,
        offset: Int
    ): Flow<List<Expense>>

    fun searchExpenseCandidates(
        query: String,
        limit: Int,
        offset: Int
    ): Flow<List<Expense>>

    suspend fun getAllExpensesSnapshot(): List<Expense>

    suspend fun getRecentExpensesSnapshot(limit: Int): List<Expense>

    suspend fun getExpensesSnapshotBetween(startMillis: Long, endMillis: Long): List<Expense>

    suspend fun getExpenseById(id: String): Expense?
}

internal interface IncomeReadRepository {
    fun getAllIncomes(): Flow<List<Income>>

    fun getIncomeCategoryUsageCounts(): Flow<List<CategoryUsageCountRow>>

    fun getIncomesBetween(startMillis: Long, endMillis: Long): Flow<List<Income>>

    fun getIncomesPageBetween(
        startMillis: Long,
        endMillis: Long,
        limit: Int,
        offset: Int
    ): Flow<List<Income>>

    fun searchIncomeCandidates(
        query: String,
        limit: Int,
        offset: Int
    ): Flow<List<Income>>

    suspend fun getAllIncomesSnapshot(): List<Income>

    suspend fun getIncomesSnapshotBetween(startMillis: Long, endMillis: Long): List<Income>

    suspend fun getIncomeById(id: String): Income?
}

internal interface DashboardReadRepository {
    fun getDashboardMonthSummary(year: Int, month: Int): Flow<DashboardMonthSummary>

    fun getDashboardBalanceTrend(
        selectedYear: Int,
        selectedMonth: Int,
        trailingMonthCount: Int = 6
    ): Flow<DashboardBalanceTrend>

    fun getDashboardRecentTransactions(limit: Int = 15): Flow<List<DashboardRecentTransaction>>

    suspend fun getWidgetMonthSummary(year: Int, month: Int): WidgetMonthSummary
}

internal interface TransactionWriteRepository {
    suspend fun deleteExpense(id: String)

    suspend fun deleteRecurringExpenseSeries(seriesId: String)

    suspend fun deleteIncome(id: String)

    suspend fun deleteRecurringIncomeSeries(seriesId: String)

    suspend fun insertIncome(
        id: String,
        amount: Long,
        date: Long,
        description: String?,
        categoryId: String?,
        recurringSeriesId: String?
    )

    suspend fun insertIncomes(incomes: List<PendingIncome>)

    suspend fun updateRecurringIncomeSeries(
        anchorIncomeId: String,
        seriesId: String,
        amount: Long,
        date: Long,
        description: String?,
        categoryId: String?
    )

    suspend fun insertExpenses(expenses: List<PendingExpense>)

    suspend fun updateRecurringExpenseSeries(
        anchorExpenseId: String,
        seriesId: String,
        amount: Long,
        date: Long,
        categoryId: String,
        description: String?,
        isShared: Boolean
    )
}

internal interface BudgetDataReplacementRepository {
    suspend fun replaceAllData(
        categories: List<RestoredCategory>,
        expenses: List<PendingExpense>,
        incomes: List<PendingIncome>
    )
}
