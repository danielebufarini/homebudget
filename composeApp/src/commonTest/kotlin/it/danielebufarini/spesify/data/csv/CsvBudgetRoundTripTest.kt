package it.danielebufarini.spesify.data.csv

import it.danielebufarini.spesify.data.PendingExpense
import it.danielebufarini.spesify.data.PendingIncome
import it.danielebufarini.spesify.data.RECURRING_MONTHLY_OCCURRENCES
import it.danielebufarini.spesify.data.buildRecurringMonthlyExpenses
import it.danielebufarini.spesify.data.buildRecurringMonthlyIncomes
import it.danielebufarini.spesify.database.CATEGORY_TYPE_EXPENSE
import it.danielebufarini.spesify.database.CATEGORY_TYPE_INCOME
import it.danielebufarini.spesify.database.Category
import it.danielebufarini.spesify.database.Expense
import it.danielebufarini.spesify.database.Income
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CsvBudgetRoundTripTest {

    @Test
    fun exportedCsvImportsBackToEquivalentRegularAndRecurringTransactions() = runTest {
        val fixture = RoundTripFixture.build()
        val exportedCsv = buildFullDatabaseCsvExport(
            expenses = fixture.expenses,
            incomes = fixture.incomes,
            categories = fixture.categories,
            startDate = LocalDate(2026, 1, 1),
            endDate = LocalDate(2028, 12, 31),
            localizeCategoryName = Category::name,
            unknownCategory = "Unknown"
        )

        val importStore = InMemoryCsvImportStore()
        val importResult = importBudgetItemsFromCsv(
            repository = importStore,
            csvText = exportedCsv.content
        )

        assertEquals(
            CsvImportResult(
                importedCount = fixture.expenses.size + fixture.incomes.size,
                skippedCount = 0
            ),
            importResult
        )
        assertEquals(
            fixture.toComparableRows(),
            importStore.toComparableRows()
        )
    }

    @Test
    fun recurringRowsKeepTheirCsvTransactionTypeWhenImported() = runTest {
        val csvText = """
            "type";"date";"category";"amount";"description";"shared";"recurring";"recurring_series_id"
            "expense";"2026-01-01";"Household";"587.63";"mortgage payment";"TRUE";"TRUE";"recurring-expense-019dd5f2-9014-724d-b6a3-2ee86f78ea23"
            "income";"2026-01-12";"Salary";"3000.00";"salary";"FALSE";"TRUE";"recurring-expense-019dd5f2-ae9a-7a54-ae97-d86121fe3a1d"
        """.trimIndent()

        val importStore = InMemoryCsvImportStore()
        val importResult = importBudgetItemsFromCsv(
            repository = importStore,
            csvText = csvText
        )

        assertEquals(
            CsvImportResult(
                importedCount = RECURRING_MONTHLY_OCCURRENCES * 2,
                skippedCount = 0
            ),
            importResult
        )
        assertEquals(RECURRING_MONTHLY_OCCURRENCES, importStore.getAllExpensesSnapshotBlocking().size)
        assertEquals(RECURRING_MONTHLY_OCCURRENCES, importStore.getAllIncomesSnapshotBlocking().size)
        assertTrue(
            importStore.getAllExpensesSnapshotBlocking()
                .all { expense -> expense.description == "mortgage payment" }
        )
        assertTrue(
            importStore.getAllExpensesSnapshotBlocking()
                .all { expense -> expense.recurringSeriesId?.startsWith("recurring-expense-") == true }
        )
        assertTrue(
            importStore.getAllIncomesSnapshotBlocking()
                .all { income -> income.description == "salary" }
        )
        assertTrue(
            importStore.getAllIncomesSnapshotBlocking()
                .all { income -> income.recurringSeriesId?.startsWith("recurring-income-") == true }
        )
    }

    @Test
    fun exportedCsvWithNewlinesAndSpreadsheetEscapedTextImportsBackToEquivalentRows() = runTest {
        val categories = listOf(
            category("expense_formula", "=Formula Food", CATEGORY_TYPE_EXPENSE),
            category("income_formula", "+Formula Salary", CATEGORY_TYPE_INCOME)
        )
        val expenses = listOf(
            Expense(
                id = "formula-expense",
                amount = 1299L,
                date = importDate(2026, 3, 4),
                categoryId = "expense_formula",
                description = "=Lunch\nsecond line",
                isShared = 1L,
                recurringSeriesId = null
            )
        )
        val incomes = listOf(
            Income(
                id = "formula-income",
                amount = 250000L,
                date = importDate(2026, 3, 5),
                categoryId = "income_formula",
                description = "@Salary\nsecond line",
                recurringSeriesId = null
            )
        )
        val exportedCsv = buildFullDatabaseCsvExport(
            expenses = expenses,
            incomes = incomes,
            categories = categories,
            startDate = LocalDate(2026, 3, 1),
            endDate = LocalDate(2026, 3, 31),
            localizeCategoryName = Category::name,
            unknownCategory = "Unknown"
        )

        assertTrue("'=Formula Food" in exportedCsv.content)
        assertTrue("'=Lunch\nsecond line" in exportedCsv.content)
        assertTrue("'+Formula Salary" in exportedCsv.content)
        assertTrue("'@Salary\nsecond line" in exportedCsv.content)

        val importStore = InMemoryCsvImportStore()
        val importResult = importBudgetItemsFromCsv(
            repository = importStore,
            csvText = exportedCsv.content
        )

        assertEquals(CsvImportResult(importedCount = 2, skippedCount = 0), importResult)
        assertEquals(
            comparableRows(
                expenses = expenses,
                incomes = incomes,
                categories = categories
            ),
            importStore.toComparableRows()
        )
    }

    @Test
    fun malformedQuotedCsvRowsAreSkippedWithoutCrashingImport() = runTest {
        val csvText = """
            "type";"date";"category";"amount";"description";"shared";"recurring";"recurring_series_id"
            "expense";"2026-01-01";"Food";"12.34";"valid row";"FALSE";"FALSE";""
            "income";"2026-01-02";"Salary";"56.78";"unterminated description;"FALSE";"FALSE";""
        """.trimIndent()

        val importStore = InMemoryCsvImportStore()
        val importResult = importBudgetItemsFromCsv(
            repository = importStore,
            csvText = csvText
        )

        assertEquals(CsvImportResult(importedCount = 1, skippedCount = 0), importResult)
        assertEquals(1, importStore.getAllExpensesSnapshotBlocking().size)
        assertEquals(0, importStore.getAllIncomesSnapshotBlocking().size)
    }
}

private data class RoundTripFixture(
    val categories: List<Category>,
    val expenses: List<Expense>,
    val incomes: List<Income>
) {
    fun toComparableRows(): List<ComparableTransactionRow> {
        return comparableRows(
            expenses = expenses,
            incomes = incomes,
            categories = categories
        )
    }

    companion object {
        fun build(): RoundTripFixture {
            val categories = listOf(
                category("expense_food", "Food", CATEGORY_TYPE_EXPENSE),
                category("expense_rent", "Rent", CATEGORY_TYPE_EXPENSE),
                category("income_salary", "Salary", CATEGORY_TYPE_INCOME),
                category("income_bonus", "Bonus", CATEGORY_TYPE_INCOME)
            )
            val recurringExpenses = buildRecurringMonthlyExpenses(
                amount = 58763L,
                firstDate = importDate(2026, 1, 1),
                categoryId = "expense_rent",
                description = "Monthly rent",
                isShared = true,
                recurringSeriesId = recurringExpenseSeriesId,
                idProvider = { Ids.next("recurring-expense") }
            ).map(PendingExpense::toExpense)
            val recurringIncomes = buildRecurringMonthlyIncomes(
                amount = 300000L,
                firstDate = importDate(2026, 1, 12),
                description = "Salary",
                categoryId = "income_salary",
                recurringSeriesId = recurringIncomeSeriesId,
                idProvider = { Ids.next("recurring-income") }
            ).map(PendingIncome::toIncome)

            return RoundTripFixture(
                categories = categories,
                expenses = listOf(
                    Expense(
                        id = "regular-expense",
                        amount = 1234L,
                        date = importDate(2026, 2, 3),
                        categoryId = "expense_food",
                        description = "Lunch",
                        isShared = 0L,
                        recurringSeriesId = null
                    )
                ) + recurringExpenses,
                incomes = listOf(
                    Income(
                        id = "regular-income",
                        amount = 12500L,
                        date = importDate(2026, 2, 15),
                        description = "Refund",
                        recurringSeriesId = null,
                        categoryId = "income_bonus"
                    )
                ) + recurringIncomes
            )
        }
    }
}

private const val recurringExpenseSeriesId = "recurring-expense-019dd5f2-9014-724d-b6a3-2ee86f78ea23"
private const val recurringIncomeSeriesId = "recurring-income-019dd5f2-ae9a-7a54-ae97-d86121fe3a1d"

private data class ComparableTransactionRow(
    val type: String,
    val date: Long,
    val categoryName: String?,
    val amount: Long,
    val description: String?,
    val isShared: Boolean,
    val recurringSeriesId: String?
) : Comparable<ComparableTransactionRow> {
    override fun compareTo(other: ComparableTransactionRow): Int {
        return compareValuesBy(
            this,
            other,
            ComparableTransactionRow::date,
            ComparableTransactionRow::type,
            ComparableTransactionRow::categoryName,
            ComparableTransactionRow::amount,
            ComparableTransactionRow::description,
            ComparableTransactionRow::isShared,
            ComparableTransactionRow::recurringSeriesId
        )
    }
}

private fun comparableRows(
    expenses: List<Expense>,
    incomes: List<Income>,
    categories: List<Category>
): List<ComparableTransactionRow> {
    val categoriesById = categories.associateBy(Category::id)
    return (expenses.map { expense ->
        ComparableTransactionRow(
            type = "expense",
            date = expense.date,
            categoryName = categoriesById[expense.categoryId]?.name,
            amount = expense.amount,
            description = expense.description,
            isShared = expense.isShared == 1L,
            recurringSeriesId = expense.recurringSeriesId
        )
    } + incomes.map { income ->
        ComparableTransactionRow(
            type = "income",
            date = income.date,
            categoryName = income.categoryId?.let(categoriesById::get)?.name,
            amount = income.amount,
            description = income.description,
            isShared = false,
            recurringSeriesId = income.recurringSeriesId
        )
    }).sorted()
}

private fun InMemoryCsvImportStore.toComparableRows(): List<ComparableTransactionRow> {
    return comparableRows(
        expenses = getAllExpensesSnapshotBlocking(),
        incomes = getAllIncomesSnapshotBlocking(),
        categories = getAllCategoriesSnapshotBlocking()
    )
}

private fun category(
    id: String,
    name: String,
    categoryType: String
): Category {
    return Category(
        id = id,
        name = name,
        icon = "category",
        categoryType = categoryType
    )
}

private fun PendingExpense.toExpense(): Expense {
    return Expense(
        id = id,
        amount = amount,
        date = date,
        categoryId = categoryId,
        description = description,
        isShared = if (isShared) 1L else 0L,
        recurringSeriesId = recurringSeriesId
    )
}

private fun PendingIncome.toIncome(): Income {
    return Income(
        id = id,
        amount = amount,
        date = date,
        description = description,
        recurringSeriesId = recurringSeriesId,
        categoryId = categoryId
    )
}

private fun importDate(year: Int, month: Int, day: Int): Long {
    return LocalDate(year, month, day)
        .atStartOfDayIn(TimeZone.currentSystemDefault())
        .toEpochMilliseconds()
}

private class InMemoryCsvImportStore : CsvImportStore {
    private val categories = mutableListOf<Category>()
    private val expenses = mutableListOf<Expense>()
    private val incomes = mutableListOf<Income>()

    override suspend fun seedStarterCategoriesIfEmpty() = Unit

    override suspend fun getAllCategoriesSnapshot(): List<Category> = categories.toList()

    override suspend fun getAllExpensesSnapshot(): List<Expense> = expenses.toList()

    override suspend fun getAllIncomesSnapshot(): List<Income> = incomes.toList()

    fun getAllCategoriesSnapshotBlocking(): List<Category> = categories.toList()

    fun getAllExpensesSnapshotBlocking(): List<Expense> = expenses.toList()

    fun getAllIncomesSnapshotBlocking(): List<Income> = incomes.toList()

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
        this.expenses += expenses.map(PendingExpense::toExpense)
    }

    override suspend fun insertIncomes(incomes: List<PendingIncome>) {
        this.incomes += incomes.map(PendingIncome::toIncome)
    }
}

private object Ids {
    private var value = 0

    fun next(prefix: String): String {
        value += 1
        return "$prefix-00000000-0000-4000-8000-${value.toString().padStart(12, '0')}"
    }
}
