package it.danielebufarini.homebudget.data

import it.danielebufarini.homebudget.database.Category
import it.danielebufarini.homebudget.database.CategoryUsageCountRow
import it.danielebufarini.homebudget.database.Expense
import it.danielebufarini.homebudget.database.Income
import kotlinx.coroutines.flow.Flow

internal class ExpenseRepositoryCategoryManagementAdapter(
    private val repository: ExpenseRepository
) : CategoryManagementRepository {
    override fun getAllCategories(): Flow<List<Category>> = repository.getAllCategories()

    override suspend fun getAllCategoriesSnapshot(): List<Category> =
        repository.getAllCategoriesSnapshot()

    override suspend fun insertCategory(
        id: String,
        name: String,
        icon: String,
        color: String,
        categoryType: String,
        isArchived: Boolean,
        sortOrder: Long?
    ) {
        repository.insertCategory(
            id = id,
            name = name,
            icon = icon,
            color = color,
            categoryType = categoryType,
            isArchived = isArchived,
            sortOrder = sortOrder
        )
    }

    override suspend fun updateCategory(
        id: String,
        name: String,
        icon: String,
        color: String?,
        categoryType: String?
    ) {
        repository.updateCategory(
            id = id,
            name = name,
            icon = icon,
            color = color,
            categoryType = categoryType
        )
    }

    override suspend fun seedStarterCategoriesIfEmpty() {
        repository.seedStarterCategoriesIfEmpty()
    }

    override suspend fun deleteCategory(id: String) {
        repository.deleteCategory(id)
    }

    override suspend fun isCategoryInUse(id: String): Boolean =
        repository.isCategoryInUse(id)

    override suspend fun setCategoryArchived(id: String, isArchived: Boolean) {
        repository.setCategoryArchived(id = id, isArchived = isArchived)
    }

    override suspend fun updateCategorySortOrder(id: String, sortOrder: Long) {
        repository.updateCategorySortOrder(id = id, sortOrder = sortOrder)
    }

    override suspend fun reassignCategoryTransactions(sourceCategoryId: String, targetCategoryId: String) {
        repository.reassignCategoryTransactions(
            sourceCategoryId = sourceCategoryId,
            targetCategoryId = targetCategoryId
        )
    }
}

internal class ExpenseRepositoryReadAdapter(
    private val repository: ExpenseRepository
) : ExpenseReadRepository {
    override fun getAllExpenses(): Flow<List<Expense>> = repository.getAllExpenses()

    override fun getExpenseCategoryUsageCounts(): Flow<List<CategoryUsageCountRow>> =
        repository.getExpenseCategoryUsageCounts()

    override fun getExpensesBetween(startMillis: Long, endMillis: Long): Flow<List<Expense>> =
        repository.getExpensesBetween(startMillis = startMillis, endMillis = endMillis)

    override fun getExpensesPageBetween(
        startMillis: Long,
        endMillis: Long,
        limit: Int,
        offset: Int
    ): Flow<List<Expense>> =
        repository.getExpensesPageBetween(
            startMillis = startMillis,
            endMillis = endMillis,
            limit = limit,
            offset = offset
        )

    override fun searchExpenseCandidates(query: String, limit: Int, offset: Int): Flow<List<Expense>> =
        repository.searchExpenseCandidates(query = query, limit = limit, offset = offset)

    override fun searchExpenseCandidatePage(
        query: String,
        limit: Int,
        cursor: TransactionPageCursor?
    ): Flow<TransactionSearchPage<Expense>> =
        repository.searchExpenseCandidatePage(query = query, limit = limit, cursor = cursor)

    override suspend fun getAllExpensesSnapshot(): List<Expense> =
        repository.getAllExpensesSnapshot()

    override suspend fun getRecentExpensesSnapshot(limit: Int): List<Expense> =
        repository.getRecentExpensesSnapshot(limit)

    override suspend fun getExpensesSnapshotBetween(startMillis: Long, endMillis: Long): List<Expense> =
        repository.getExpensesSnapshotBetween(startMillis = startMillis, endMillis = endMillis)

    override suspend fun getExpenseById(id: String): Expense? =
        repository.getExpenseById(id)
}

internal class ExpenseRepositoryIncomeReadAdapter(
    private val repository: ExpenseRepository
) : IncomeReadRepository {
    override fun getAllIncomes(): Flow<List<Income>> = repository.getAllIncomes()

    override fun getIncomeCategoryUsageCounts(): Flow<List<CategoryUsageCountRow>> =
        repository.getIncomeCategoryUsageCounts()

    override fun getIncomesBetween(startMillis: Long, endMillis: Long): Flow<List<Income>> =
        repository.getIncomesBetween(startMillis = startMillis, endMillis = endMillis)

    override fun getIncomesPageBetween(
        startMillis: Long,
        endMillis: Long,
        limit: Int,
        offset: Int
    ): Flow<List<Income>> =
        repository.getIncomesPageBetween(
            startMillis = startMillis,
            endMillis = endMillis,
            limit = limit,
            offset = offset
        )

    override fun searchIncomeCandidates(query: String, limit: Int, offset: Int): Flow<List<Income>> =
        repository.searchIncomeCandidates(query = query, limit = limit, offset = offset)

    override fun searchIncomeCandidatePage(
        query: String,
        limit: Int,
        cursor: TransactionPageCursor?
    ): Flow<TransactionSearchPage<Income>> =
        repository.searchIncomeCandidatePage(query = query, limit = limit, cursor = cursor)

    override suspend fun getAllIncomesSnapshot(): List<Income> =
        repository.getAllIncomesSnapshot()

    override suspend fun getIncomesSnapshotBetween(startMillis: Long, endMillis: Long): List<Income> =
        repository.getIncomesSnapshotBetween(startMillis = startMillis, endMillis = endMillis)

    override suspend fun getIncomeById(id: String): Income? =
        repository.getIncomeById(id)
}

internal class ExpenseRepositoryDashboardReadAdapter(
    private val repository: ExpenseRepository
) : DashboardReadRepository {
    override fun getDashboardMonthSummary(year: Int, month: Int): Flow<DashboardMonthSummary> =
        repository.getDashboardMonthSummary(year = year, month = month)

    override fun getDashboardBalanceTrend(
        selectedYear: Int,
        selectedMonth: Int,
        trailingMonthCount: Int
    ): Flow<DashboardBalanceTrend> =
        repository.getDashboardBalanceTrend(
            selectedYear = selectedYear,
            selectedMonth = selectedMonth,
            trailingMonthCount = trailingMonthCount
        )

    override fun getDashboardRecentTransactions(limit: Int): Flow<List<DashboardRecentTransaction>> =
        repository.getDashboardRecentTransactions(limit = limit)

    override suspend fun getWidgetMonthSummary(year: Int, month: Int): WidgetMonthSummary =
        repository.getWidgetMonthSummary(year = year, month = month)
}

internal class ExpenseRepositoryTransactionWriteAdapter(
    private val repository: ExpenseRepository
) : TransactionWriteRepository {
    override suspend fun deleteExpense(id: String) {
        repository.deleteExpense(id)
    }

    override suspend fun deleteRecurringExpenseSeries(seriesId: String) {
        repository.deleteRecurringExpenseSeries(seriesId)
    }

    override suspend fun deleteIncome(id: String) {
        repository.deleteIncome(id)
    }

    override suspend fun deleteRecurringIncomeSeries(seriesId: String) {
        repository.deleteRecurringIncomeSeries(seriesId)
    }

    override suspend fun insertIncome(
        id: String,
        amount: Long,
        date: Long,
        description: String?,
        categoryId: String?,
        recurringSeriesId: String?
    ) {
        repository.insertIncome(
            id = id,
            amount = amount,
            date = date,
            description = description,
            categoryId = categoryId,
            recurringSeriesId = recurringSeriesId
        )
    }

    override suspend fun insertIncomes(incomes: List<PendingIncome>) {
        repository.insertIncomes(incomes)
    }

    override suspend fun updateRecurringIncomeSeries(
        anchorIncomeId: String,
        seriesId: String,
        amount: Long,
        date: Long,
        description: String?,
        categoryId: String?
    ) {
        repository.updateRecurringIncomeSeries(
            anchorIncomeId = anchorIncomeId,
            seriesId = seriesId,
            amount = amount,
            date = date,
            description = description,
            categoryId = categoryId
        )
    }

    override suspend fun insertExpenses(expenses: List<PendingExpense>) {
        repository.insertExpenses(expenses)
    }

    override suspend fun updateRecurringExpenseSeries(
        anchorExpenseId: String,
        seriesId: String,
        amount: Long,
        date: Long,
        categoryId: String,
        description: String?,
        isShared: Boolean
    ) {
        repository.updateRecurringExpenseSeries(
            anchorExpenseId = anchorExpenseId,
            seriesId = seriesId,
            amount = amount,
            date = date,
            categoryId = categoryId,
            description = description,
            isShared = isShared
        )
    }
}

internal class ExpenseRepositoryBudgetDataReplacementAdapter(
    private val repository: ExpenseRepository
) : BudgetDataReplacementRepository {
    override suspend fun replaceAllData(
        categories: List<RestoredCategory>,
        expenses: List<PendingExpense>,
        incomes: List<PendingIncome>
    ) {
        repository.replaceAllData(
            categories = categories,
            expenses = expenses,
            incomes = incomes
        )
    }
}
