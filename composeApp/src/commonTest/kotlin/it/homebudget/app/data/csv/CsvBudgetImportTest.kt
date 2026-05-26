package it.homebudget.app.data.csv

import it.homebudget.app.data.PendingExpense
import it.homebudget.app.data.PendingIncome
import it.homebudget.app.data.RECURRING_MONTHLY_OCCURRENCES
import it.homebudget.app.database.CATEGORY_TYPE_EXPENSE
import it.homebudget.app.database.CATEGORY_TYPE_INCOME
import it.homebudget.app.database.Category
import it.homebudget.app.database.Expense
import it.homebudget.app.database.Income
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CsvBudgetImportTest {

    @Test
    fun importBudgetItemsFromCsv_importsExpenseAndRecurringIncomeAfterDatabaseClear() = runTest {
        val store = InMemoryCsvImportStore()

        importAndVerifyFixture(store)
        store.clear()
        importAndVerifyFixture(store)
    }

    private suspend fun importAndVerifyFixture(store: InMemoryCsvImportStore) {
        val result = importBudgetItemsFromCsv(
            repository = store,
            csvText = importFixtureCsv
        )

        assertEquals(
            CsvImportResult(
                importedCount = 1 + RECURRING_MONTHLY_OCCURRENCES,
                skippedCount = 0
            ),
            result
        )

        val expenses = store.getAllExpensesSnapshot()
        assertEquals(1, expenses.size)
        expenses.single().let { expense ->
            assertEquals(1234L, expense.amount)
            assertEquals(importDate(2026, 5, 10), expense.date)
            assertEquals("Lunch", expense.description)
            assertEquals(1L, expense.isShared)
            assertEquals(null, expense.recurringSeriesId)

            val category = assertNotNull(store.getAllCategoriesSnapshot().firstOrNull { it.id == expense.categoryId })
            assertEquals("Groceries", category.name)
            assertEquals(CATEGORY_TYPE_EXPENSE, category.categoryType)
        }

        val incomes = store.getAllIncomesSnapshot()
        assertEquals(RECURRING_MONTHLY_OCCURRENCES, incomes.size)
        assertEquals(setOf("salary-series"), incomes.mapNotNull(Income::recurringSeriesId).toSet())
        assertEquals(setOf(250000L), incomes.map(Income::amount).toSet())
        assertEquals(setOf("Salary"), incomes.map(Income::description).toSet())

        val firstIncome = incomes.minBy(Income::date)
        assertEquals(importDate(2026, 5, 31), firstIncome.date)
        val incomeCategory = assertNotNull(
            store.getAllCategoriesSnapshot().firstOrNull { it.id == firstIncome.categoryId }
        )
        assertEquals("Payroll", incomeCategory.name)
        assertEquals(CATEGORY_TYPE_INCOME, incomeCategory.categoryType)
    }
}

private val importFixtureCsv = """
    "type";"date";"category";"amount";"description";"shared";"recurring";"recurring_series_id"
    "expense";"2026-05-10";"Groceries";"12.34";"Lunch";"true";"false";""
    "income";"2026-05-31";"Payroll";"2500.00";"Salary";"false";"true";"salary-series"
""".trimIndent()

private fun importDate(year: Int, month: Int, day: Int): Long =
    LocalDate(year, month, day)
        .atStartOfDayIn(TimeZone.currentSystemDefault())
        .toEpochMilliseconds()

private class InMemoryCsvImportStore : CsvImportStore {
    private val categories = mutableListOf<Category>()
    private val expenses = mutableListOf<Expense>()
    private val incomes = mutableListOf<Income>()

    fun clear() {
        categories.clear()
        expenses.clear()
        incomes.clear()
    }

    override suspend fun seedStarterCategoriesIfEmpty() = Unit

    override suspend fun getAllCategoriesSnapshot(): List<Category> = categories.toList()

    override suspend fun getAllExpensesSnapshot(): List<Expense> = expenses.toList()

    override suspend fun getAllIncomesSnapshot(): List<Income> = incomes.toList()

    override suspend fun insertCategory(
        id: String,
        name: String,
        icon: String,
        color: String,
        categoryType: String,
        isArchived: Boolean,
        sortOrder: Long
    ) {
        categories += Category(
            id = id,
            name = name,
            icon = icon,
            color = color,
            categoryType = categoryType,
            isArchived = if (isArchived) 1L else 0L,
            sortOrder = sortOrder
        )
    }

    override suspend fun insertExpenses(expenses: List<PendingExpense>) {
        this.expenses += expenses.map { expense ->
            Expense(
                id = expense.id,
                amount = expense.amount,
                date = expense.date,
                categoryId = expense.categoryId,
                description = expense.description,
                isShared = if (expense.isShared) 1L else 0L,
                recurringSeriesId = expense.recurringSeriesId
            )
        }
    }

    override suspend fun insertIncomes(incomes: List<PendingIncome>) {
        this.incomes += incomes.map { income ->
            Income(
                id = income.id,
                amount = income.amount,
                date = income.date,
                description = income.description,
                recurringSeriesId = income.recurringSeriesId,
                categoryId = income.categoryId
            )
        }
    }
}
