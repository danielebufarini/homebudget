package it.homebudget.app.ui.screens

import it.homebudget.app.database.Category
import it.homebudget.app.database.Expense
import it.homebudget.app.database.Income
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals

class TransactionListTransformersTest {

    private val shortMonthNames = listOf(
        "Jan",
        "Feb",
        "Mar",
        "Apr",
        "May",
        "Jun",
        "Jul",
        "Aug",
        "Sep",
        "Oct",
        "Nov",
        "Dec"
    )

    @Test
    fun buildGroupedExpensesState_groupsByCategoryWithStableOrdering() {
        val state = buildGroupedExpensesState(
            expenses = listOf(
                expense(id = "2", amount = 300, date = LocalDate(2026, 5, 11), categoryId = "food"),
                expense(id = "1", amount = 1200, date = LocalDate(2026, 5, 12), categoryId = "rent"),
                expense(id = "3", amount = 200, date = LocalDate(2026, 5, 10), categoryId = "food")
            ),
            categoriesById = mapOf(
                "food" to Category(id = "food", name = "Food", icon = "restaurant", isArchived = 1L),
                "rent" to Category(id = "rent", name = "Rent", icon = "home", isArchived = 1L)
            ),
            groupingMode = ExpenseGroupingMode.ByCategory,
            includeExpense = { true },
            includeCategory = { true },
            resolveCategoryName = { it.name },
            unknownCategoryLabel = "Unknown",
            shortMonthNames = shortMonthNames
        )

        assertEquals(listOf("Food", "Rent"), state.sections.map(ExpenseSection::title))
        assertEquals(listOf("2", "3"), state.sections.first().expenses.map(Expense::id))
        assertEquals(1700L, state.totalAmount)
    }

    @Test
    fun buildGroupedExpensesState_groupsByDateWithFormattedHeaders() {
        val state = buildGroupedExpensesState(
            expenses = listOf(
                expense(id = "2", amount = 300, date = LocalDate(2026, 5, 11), categoryId = "food"),
                expense(id = "1", amount = 1200, date = LocalDate(2026, 5, 12), categoryId = "rent"),
                expense(id = "3", amount = 200, date = LocalDate(2026, 5, 12), categoryId = "food")
            ),
            categoriesById = mapOf(
                "food" to Category(id = "food", name = "Food", icon = "restaurant", isArchived = 1L),
                "rent" to Category(id = "rent", name = "Rent", icon = "home", isArchived = 1L)
            ),
            groupingMode = ExpenseGroupingMode.ByDate,
            includeExpense = { true },
            includeCategory = { true },
            resolveCategoryName = { it.name },
            unknownCategoryLabel = "Unknown",
            shortMonthNames = shortMonthNames
        )

        assertEquals(listOf("12 May 2026", "11 May 2026"), state.sections.map(ExpenseSection::title))
        assertEquals(listOf("3", "1"), state.sections.first().expenses.map(Expense::id))
    }

    @Test
    fun buildGroupedIncomesState_groupsByDateAndKeepsTotals() {
        val state = buildGroupedIncomesState(
            incomes = listOf(
                income(id = "2", amount = 2000, date = LocalDate(2026, 5, 15)),
                income(id = "1", amount = 2500, date = LocalDate(2026, 5, 15)),
                income(id = "3", amount = 800, date = LocalDate(2026, 5, 1))
            ),
            categoriesById = emptyMap(),
            groupingMode = ExpenseGroupingMode.ByDate,
            resolveCategoryName = { it.name },
            unknownCategoryLabel = "Unknown",
            shortMonthNames = shortMonthNames
        )

        assertEquals(listOf("date:2026-05-15", "date:2026-05-01"), state.sections.map(IncomeSection::key))
        assertEquals(listOf("2", "1"), state.sections.first().incomes.map(Income::id))
        assertEquals(4500L, state.sections.first().totalAmount)
        assertEquals(5300L, state.totalAmount)
    }

    @Test
    fun transactionSearch_filtersExpensesAndIncomesByDescriptionCategoryAmountAndDate() {
        val categories = mapOf(
            "food" to Category(id = "food", name = "Food", icon = "restaurant", isArchived = 0L),
            "salary" to Category(id = "salary", name = "Salary", icon = "work", isArchived = 0L)
        )

        val expenseState = buildGroupedExpensesState(
            expenses = listOf(
                expense(
                    id = "dinner",
                    amount = 3299,
                    date = LocalDate(2026, 5, 12),
                    categoryId = "food",
                    description = "Team dinner"
                ),
                expense(
                    id = "fuel",
                    amount = 5000,
                    date = LocalDate(2026, 5, 13),
                    categoryId = "food",
                    description = "Fuel"
                )
            ),
            categoriesById = categories,
            groupingMode = ExpenseGroupingMode.ByDate,
            includeExpense = { true },
            includeCategory = { true },
            resolveCategoryName = { it.name },
            unknownCategoryLabel = "Unknown",
            shortMonthNames = shortMonthNames,
            searchQuery = "food 32.99",
            currencySymbol = "$"
        )
        val incomeState = buildGroupedIncomesState(
            incomes = listOf(
                income(
                    id = "paycheck",
                    amount = 250000,
                    date = LocalDate(2026, 5, 20),
                    description = "May paycheck",
                    categoryId = "salary"
                ),
                income(
                    id = "bonus",
                    amount = 75000,
                    date = LocalDate(2026, 6, 1),
                    description = "Bonus",
                    categoryId = "salary"
                )
            ),
            categoriesById = categories,
            groupingMode = ExpenseGroupingMode.ByDate,
            resolveCategoryName = { it.name },
            unknownCategoryLabel = "Unknown",
            shortMonthNames = shortMonthNames,
            searchQuery = "2026-05 paycheck",
            currencySymbol = "$"
        )

        assertEquals(listOf("dinner"), expenseState.visibleExpenses.map(Expense::id))
        assertEquals(listOf("paycheck"), incomeState.visibleIncomes.map(Income::id))
    }
}

private fun expense(
    id: String,
    amount: Int,
    date: LocalDate,
    categoryId: String,
    description: String? = null
) = Expense(
    id = id,
    amount = amount.toLong(),
    date = date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
    categoryId = categoryId,
    description = description,
    isShared = 0L,
    recurringSeriesId = null
)

private fun income(
    id: String,
    amount: Int,
    date: LocalDate,
    description: String? = null,
    categoryId: String? = null
) = Income(
    id = id,
    amount = amount.toLong(),
    date = date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
    description = description,
    recurringSeriesId = null,
    categoryId = categoryId
)
