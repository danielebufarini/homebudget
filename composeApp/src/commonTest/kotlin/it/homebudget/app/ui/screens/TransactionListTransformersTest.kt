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
                "food" to Category("food", "Food", "restaurant", 1L),
                "rent" to Category("rent", "Rent", "home", 1L)
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
                "food" to Category("food", "Food", "restaurant", 1L),
                "rent" to Category("rent", "Rent", "home", 1L)
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
            listOf(
                income(id = "2", amount = 2000, date = LocalDate(2026, 5, 15)),
                income(id = "1", amount = 2500, date = LocalDate(2026, 5, 15)),
                income(id = "3", amount = 800, date = LocalDate(2026, 5, 1))
            )
        )

        assertEquals(listOf("2026-05-15", "2026-05-01"), state.sections.map(IncomeSection::key))
        assertEquals(listOf("2", "1"), state.sections.first().incomes.map(Income::id))
        assertEquals(4500L, state.sections.first().totalAmount)
        assertEquals(5300L, state.totalAmount)
    }
}

private fun expense(
    id: String,
    amount: Int,
    date: LocalDate,
    categoryId: String
) = Expense(
    id = id,
    amount = amount.toLong(),
    date = date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
    categoryId = categoryId,
    description = null,
    isShared = 0L,
    recurringSeriesId = null
)

private fun income(
    id: String,
    amount: Int,
    date: LocalDate
) = Income(
    id = id,
    amount = amount.toLong(),
    date = date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
    description = null,
    recurringSeriesId = null
)
