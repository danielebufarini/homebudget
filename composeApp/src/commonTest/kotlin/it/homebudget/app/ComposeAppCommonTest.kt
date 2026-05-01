package it.homebudget.app

import com.ionspin.kotlin.bignum.integer.toBigInteger
import it.homebudget.app.data.*
import it.homebudget.app.database.Category
import it.homebudget.app.database.Expense
import it.homebudget.app.database.Income
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

    @Test
    fun exportBudgetItemsToCsv_exportsExpenseFlagsAndFiltersRange() {
        val export = buildExpensesCsvExport(
            expenses = listOf(
                expense(
                    id = "expense-in-range",
                    amount = 1234.toBigInteger(),
                    date = LocalDate(2026, 5, 10).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
                    categoryId = "default_1",
                    description = "Groceries",
                    isShared = 1L,
                    recurringSeriesId = "series-1"
                ),
                expense(
                    id = "expense-out-of-range",
                    amount = 500.toBigInteger(),
                    date = LocalDate(2026, 6, 1).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
                    categoryId = "default_4",
                    description = null,
                    isShared = 0L,
                    recurringSeriesId = null
                )
            ),
            categories = listOf(
                Category("default_1", "Cibo", "restaurant", 0L)
            ),
            startDate = LocalDate(2026, 5, 1),
            endDate = LocalDate(2026, 5, 31)
        )

        assertEquals("expenses_2026-05-01_2026-05-31.csv", export.fileName)
        assertEquals(
            "\"type\";\"date\";\"category\";\"amount\";\"description\";\"shared\";\"recurring\";\"recurring_series_id\"\n" +
                "\"expense\";\"2026-05-10\";\"Food\";\"12.34\";\"Groceries\";\"true\";\"true\";\"series-1\"\n",
            export.content
        )
    }

    @Test
    fun exportBudgetItemsToCsv_exportsIncomeFlags() {
        val export = buildIncomesCsvExport(
            incomes = listOf(
                income(
                    id = "income-1",
                    amount = 320000.toBigInteger(),
                    date = LocalDate(2026, 5, 15).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
                    description = "Salary",
                    recurringSeriesId = "income-series"
                )
            ),
            startDate = LocalDate(2026, 5, 1),
            endDate = LocalDate(2026, 5, 31)
        )

        assertEquals(
            "\"type\";\"date\";\"category\";\"amount\";\"description\";\"shared\";\"recurring\";\"recurring_series_id\"\n" +
                "\"income\";\"2026-05-15\";\"\";\"3200.00\";\"Salary\";\"false\";\"true\";\"income-series\"\n",
            export.content
        )
    }
}

private fun expense(
    id: String,
    amount: com.ionspin.kotlin.bignum.integer.BigInteger,
    date: Long,
    categoryId: String,
    description: String?,
    isShared: Long,
    recurringSeriesId: String?
) = Expense(
    id = id,
    amount = amount,
    date = date,
    categoryId = categoryId,
    description = description,
    isShared = isShared,
    recurringSeriesId = recurringSeriesId
)

private fun income(
    id: String,
    amount: com.ionspin.kotlin.bignum.integer.BigInteger,
    date: Long,
    description: String?,
    recurringSeriesId: String?
) = Income(
    id = id,
    amount = amount,
    date = date,
    description = description,
    recurringSeriesId = recurringSeriesId
)
