package it.danielebufarini.spesify.data

import it.danielebufarini.spesify.database.Expense
import kotlin.test.Test
import kotlin.test.assertEquals

class RecurringExpensesTest {
    @Test
    fun buildRecurringExpenseOverview_keepsOnlyRecurringExpensesAndSumsMinorUnits() {
        val overview = buildRecurringExpenseOverview(
            listOf(
                expense(id = "one-off", amount = 99_999L, date = 2_000L, recurringSeriesId = null),
                expense(id = "netflix", amount = 1_299L, date = 3_000L, recurringSeriesId = "series-a"),
                expense(id = "rent", amount = 120_000L, date = 1_000L, recurringSeriesId = "series-b")
            )
        )

        assertEquals(listOf("rent", "netflix"), overview.expenses.map(Expense::id))
        assertEquals(121_299L, overview.totalAmount)
        assertEquals(1_000L, overview.nextOccurrenceDate)
    }

    @Test
    fun buildRecurringExpenseOverview_returnsEmptyOverviewWhenThereAreNoRecurringExpenses() {
        val overview = buildRecurringExpenseOverview(
            listOf(
                expense(id = "groceries", amount = 4_250L, date = 1_000L, recurringSeriesId = null)
            )
        )

        assertEquals(emptyList(), overview.expenses)
        assertEquals(0L, overview.totalAmount)
        assertEquals(null, overview.nextOccurrenceDate)
    }

    private fun expense(
        id: String,
        amount: Long,
        date: Long,
        recurringSeriesId: String?
    ): Expense = Expense(
        id = id,
        amount = amount,
        date = date,
        categoryId = "category",
        description = id,
        isShared = 0L,
        recurringSeriesId = recurringSeriesId
    )
}
