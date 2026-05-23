package it.homebudget.app.data
import it.homebudget.app.database.CATEGORY_TYPE_EXPENSE
import it.homebudget.app.database.Category
import it.homebudget.app.database.DEFAULT_CATEGORY_COLOR
import it.homebudget.app.database.Expense
import it.homebudget.app.database.Income
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

data class RestoredCategory(
    val id: String,
    val name: String,
    val icon: String,
    val color: String = DEFAULT_CATEGORY_COLOR,
    val categoryType: String = CATEGORY_TYPE_EXPENSE,
    val isArchived: Boolean = false,
    val sortOrder: Long = 0L
)

data class DashboardMonthTotal(
    val year: Int,
    val month: Int,
    val amount: Long
)

data class DashboardCategoryTotal(
    val categoryId: String,
    val amount: Long
)

data class DashboardMonthSummary(
    val expenseCount: Int,
    val totalAmount: Long,
    val incomeAmount: Long,
    val sharedAmount: Long,
    val averageAmount: Long,
    val topCategoryId: String?,
    val highestDayOfMonth: Int?,
    val highestDayAmount: Long,
    val categoryTotals: List<DashboardCategoryTotal>
)

data class DashboardCashFlow(
    val expenseTotalsByMonth: List<DashboardMonthTotal>,
    val incomeTotalsByMonth: List<DashboardMonthTotal>
)

data class WidgetMonthSummary(
    val expenseAmount: Long,
    val incomeAmount: Long
)

class ExpenseRepository(
    private val categoryRepository: CategoryRepository,
    private val expenseEntryRepository: ExpenseEntryRepository,
    private val incomeRepository: IncomeRepository,
    private val dashboardRepository: DashboardRepository,
    private val recurringTransactionService: RecurringTransactionService,
    private val dataReplacementService: DataReplacementService
) {
    fun getAllCategories(): Flow<List<Category>> = categoryRepository.getAllCategories()

    suspend fun insertCategory(
        id: String,
        name: String,
        icon: String,
        color: String = DEFAULT_CATEGORY_COLOR,
        categoryType: String = CATEGORY_TYPE_EXPENSE,
        isArchived: Boolean = false,
        sortOrder: Long? = null
    ) {
        categoryRepository.insertCategory(
            id = id,
            name = name,
            icon = icon,
            color = color,
            categoryType = categoryType,
            isArchived = isArchived,
            sortOrder = sortOrder
        )
    }

    suspend fun updateCategory(
        id: String,
        name: String,
        icon: String,
        color: String? = null,
        categoryType: String? = null
    ) {
        val existingCategory = categoryRepository.getCategoryById(id)
            ?: error("Category $id not found")
        categoryRepository.updateCategory(
            id = id,
            name = name,
            icon = icon,
            color = color ?: existingCategory.color,
            categoryType = categoryType ?: existingCategory.categoryType
        )
    }

    suspend fun seedStarterCategoriesIfEmpty() {
        categoryRepository.seedStarterCategoriesIfEmpty()
    }

    fun getAllExpenses(): Flow<List<Expense>> = expenseEntryRepository.getAllExpenses()

    fun getExpensesBetween(startMillis: Long, endMillis: Long): Flow<List<Expense>> =
        expenseEntryRepository.getExpensesBetween(startMillis, endMillis)

    suspend fun getAllExpensesSnapshot(): List<Expense> = expenseEntryRepository.getAllExpensesSnapshot()

    suspend fun getExpensesSnapshotBetween(startMillis: Long, endMillis: Long): List<Expense> =
        expenseEntryRepository.getExpensesBetween(startMillis, endMillis).first()

    fun getAllIncomes(): Flow<List<Income>> = incomeRepository.getAllIncomes()

    fun getIncomesBetween(startMillis: Long, endMillis: Long): Flow<List<Income>> =
        incomeRepository.getIncomesBetween(startMillis, endMillis)

    suspend fun getAllIncomesSnapshot(): List<Income> = incomeRepository.getAllIncomesSnapshot()

    suspend fun getIncomesSnapshotBetween(startMillis: Long, endMillis: Long): List<Income> =
        incomeRepository.getIncomesBetween(startMillis, endMillis).first()

    suspend fun getAllCategoriesSnapshot(): List<Category> = categoryRepository.getAllCategoriesSnapshot()

    fun getDashboardMonthSummary(year: Int, month: Int): Flow<DashboardMonthSummary> {
        return dashboardRepository.getDashboardMonthSummary(year = year, month = month)
    }

    fun getDashboardCashFlow(
        selectedYear: Int,
        selectedMonth: Int,
        trailingMonthCount: Int = 6
    ): Flow<DashboardCashFlow> {
        return dashboardRepository.getDashboardCashFlow(
            selectedYear = selectedYear,
            selectedMonth = selectedMonth,
            trailingMonthCount = trailingMonthCount
        )
    }

    suspend fun getWidgetMonthSummary(year: Int, month: Int): WidgetMonthSummary {
        return dashboardRepository.getWidgetMonthSummary(year = year, month = month)
    }

    suspend fun getExpenseById(id: String): Expense? = expenseEntryRepository.getExpenseById(id)

    suspend fun getIncomeById(id: String): Income? = incomeRepository.getIncomeById(id)

    suspend fun deleteExpense(id: String) {
        expenseEntryRepository.deleteExpense(id)
    }

    suspend fun deleteCategory(id: String) {
        categoryRepository.deleteCategory(id)
    }

    suspend fun isCategoryInUse(id: String): Boolean {
        return categoryRepository.isCategoryInUse(id)
    }

    suspend fun setCategoryArchived(id: String, isArchived: Boolean) {
        categoryRepository.setCategoryArchived(id = id, isArchived = isArchived)
    }

    suspend fun updateCategorySortOrder(id: String, sortOrder: Long) {
        categoryRepository.updateCategorySortOrder(id = id, sortOrder = sortOrder)
    }

    suspend fun reassignCategoryTransactions(sourceCategoryId: String, targetCategoryId: String) {
        categoryRepository.reassignCategoryTransactions(
            sourceCategoryId = sourceCategoryId,
            targetCategoryId = targetCategoryId
        )
    }

    suspend fun deleteRecurringExpenseSeries(seriesId: String) {
        expenseEntryRepository.deleteRecurringExpenseSeries(seriesId)
    }

    suspend fun deleteIncome(id: String) {
        incomeRepository.deleteIncome(id)
    }

    suspend fun deleteRecurringIncomeSeries(seriesId: String) {
        incomeRepository.deleteRecurringIncomeSeries(seriesId)
    }

    suspend fun insertIncome(
        id: String,
        amount: Long,
        date: Long,
        description: String?,
        categoryId: String? = null,
        recurringSeriesId: String? = null
    ) {
        incomeRepository.insertIncome(
            id = id,
            amount = amount,
            date = date,
            description = description,
            categoryId = categoryId,
            recurringSeriesId = recurringSeriesId
        )
    }

    suspend fun insertIncomes(incomes: List<PendingIncome>) {
        incomeRepository.insertIncomes(incomes)
    }

    suspend fun updateRecurringIncomeSeries(
        anchorIncomeId: String,
        seriesId: String,
        amount: Long,
        date: Long,
        description: String?,
        categoryId: String? = null
    ) {
        recurringTransactionService.updateRecurringIncomeSeries(
            anchorIncomeId = anchorIncomeId,
            seriesId = seriesId,
            amount = amount,
            date = date,
            description = description,
            categoryId = categoryId
        )
    }

    suspend fun insertExpenses(expenses: List<PendingExpense>) {
        expenseEntryRepository.insertExpenses(expenses)
    }

    suspend fun updateRecurringExpenseSeries(
        anchorExpenseId: String,
        seriesId: String,
        amount: Long,
        date: Long,
        categoryId: String,
        description: String?,
        isShared: Boolean
    ) {
        recurringTransactionService.updateRecurringExpenseSeries(
            anchorExpenseId = anchorExpenseId,
            seriesId = seriesId,
            amount = amount,
            date = date,
            categoryId = categoryId,
            description = description,
            isShared = isShared
        )
    }

    suspend fun replaceAllData(
        categories: List<RestoredCategory>,
        expenses: List<PendingExpense>,
        incomes: List<PendingIncome>
    ) {
        dataReplacementService.replaceAllData(
            categories = categories,
            expenses = expenses,
            incomes = incomes
        )
    }
}
