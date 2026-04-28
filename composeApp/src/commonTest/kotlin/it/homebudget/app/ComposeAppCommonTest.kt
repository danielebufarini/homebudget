package it.homebudget.app

import com.ionspin.kotlin.bignum.integer.toBigInteger
import it.homebudget.app.data.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals

class ComposeAppCommonTest {

    @Test
    fun splitAmountIntoInstallments_preservesTotalAndDistributesRemainder() {
        val installments = splitAmountIntoInstallments(1000.toBigInteger(), 3)

        assertEquals(
            listOf(334.toBigInteger(), 333.toBigInteger(), 333.toBigInteger()),
            installments
        )
        assertEquals(1000.toBigInteger(), installments.reduce { acc, value -> acc + value })
    }

    @Test
    fun buildPendingExpenses_offsetsInstallmentDatesByMonth() {
        val timeZone = TimeZone.UTC
        val firstDate = LocalDate(2026, 1, 31).atStartOfDayIn(timeZone).toEpochMilliseconds()
        var nextId = 0

        val expenses = buildPendingExpenses(
            amount = 1000.toBigInteger(),
            firstDate = firstDate,
            installments = 3,
            categoryId = "food",
            description = "Groceries",
            isShared = false,
            idProvider = { "expense-${nextId++}" },
            timeZone = timeZone
        )

        assertEquals(3, expenses.size)
        assertEquals(firstDate, expenses[0].date)
        assertEquals(listOf(null, null, null), expenses.map { it.recurringSeriesId })
        assertEquals(LocalDate(2026, 2, 28).atStartOfDayIn(timeZone).toEpochMilliseconds(), expenses[1].date)
        assertEquals(LocalDate(2026, 3, 31).atStartOfDayIn(timeZone).toEpochMilliseconds(), expenses[2].date)
    }

    @Test
    fun buildRecurringMonthlyExpenses_repeatsFullAmountAcrossMonths() {
        val timeZone = TimeZone.UTC
        val firstDate = LocalDate(2026, 1, 31).atStartOfDayIn(timeZone).toEpochMilliseconds()
        var nextId = 0

        val expenses = buildRecurringMonthlyExpenses(
            amount = 1999.toBigInteger(),
            firstDate = firstDate,
            categoryId = "rent",
            description = "Rent",
            isShared = true,
            recurringSeriesId = "series-1",
            idProvider = { "recurring-${nextId++}" },
            occurrences = 3,
            timeZone = timeZone
        )

        assertEquals(3, expenses.size)
        assertEquals(listOf(1999.toBigInteger(), 1999.toBigInteger(), 1999.toBigInteger()), expenses.map { it.amount })
        assertEquals(listOf("series-1", "series-1", "series-1"), expenses.map { it.recurringSeriesId })
        assertEquals(firstDate, expenses[0].date)
        assertEquals(LocalDate(2026, 2, 28).atStartOfDayIn(timeZone).toEpochMilliseconds(), expenses[1].date)
        assertEquals(LocalDate(2026, 3, 31).atStartOfDayIn(timeZone).toEpochMilliseconds(), expenses[2].date)
    }

    @Test
    fun buildRecurringMonthlyIncomes_repeatsFullAmountAcrossMonths() {
        val timeZone = TimeZone.UTC
        val firstDate = LocalDate(2026, 1, 31).atStartOfDayIn(timeZone).toEpochMilliseconds()
        var nextId = 0

        val incomes = buildRecurringMonthlyIncomes(
            amount = 3200.toBigInteger(),
            firstDate = firstDate,
            description = "Salary",
            recurringSeriesId = "income-series-1",
            idProvider = { "income-${nextId++}" },
            occurrences = 3,
            timeZone = timeZone
        )

        assertEquals(3, incomes.size)
        assertEquals(
            listOf(3200.toBigInteger(), 3200.toBigInteger(), 3200.toBigInteger()),
            incomes.map { it.amount }
        )
        assertEquals(
            listOf("income-series-1", "income-series-1", "income-series-1"),
            incomes.map { it.recurringSeriesId }
        )
        assertEquals(firstDate, incomes[0].date)
        assertEquals(LocalDate(2026, 2, 28).atStartOfDayIn(timeZone).toEpochMilliseconds(), incomes[1].date)
        assertEquals(LocalDate(2026, 3, 31).atStartOfDayIn(timeZone).toEpochMilliseconds(), incomes[2].date)
    }

    @Test
    fun recurringMonthlyOccurrences_defaultMatchesTwentyYears() {
        assertEquals(240, RECURRING_MONTHLY_OCCURRENCES)
    }
}
