package it.danielebufarini.spesify

import it.danielebufarini.spesify.data.RECURRING_MONTHLY_OCCURRENCES
import it.danielebufarini.spesify.data.buildPendingExpenses
import it.danielebufarini.spesify.data.buildRecurringMonthlyExpenses
import it.danielebufarini.spesify.data.buildRecurringMonthlyExpensesFromExistingExpense
import it.danielebufarini.spesify.data.buildRecurringMonthlyIncomes
import it.danielebufarini.spesify.data.csv.buildExpensesCsvExport
import it.danielebufarini.spesify.data.csv.buildIncomesCsvExport
import it.danielebufarini.spesify.data.parseAmountInput
import it.danielebufarini.spesify.data.parseBudgetBackup
import it.danielebufarini.spesify.data.parseSerializedAmount
import it.danielebufarini.spesify.data.splitAmountIntoInstallments
import it.danielebufarini.spesify.database.Category
import it.danielebufarini.spesify.database.Expense
import it.danielebufarini.spesify.database.Income
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals

class ComposeAppCommonTest {

    @Test
    fun splitAmountIntoInstallments_preservesTotalAndDistributesRemainder() {
        val installments = splitAmountIntoInstallments(1000L, 3)

        assertEquals(
            listOf(334L, 333L, 333L),
            installments
        )
        assertEquals(1000L, installments.reduce { acc, value -> acc + value })
    }

    @Test
    fun buildPendingExpenses_offsetsInstallmentDatesByMonth() {
        val timeZone = TimeZone.UTC
        val firstDate = LocalDate(2026, 1, 31).atStartOfDayIn(timeZone).toEpochMilliseconds()
        var nextId = 0

        val expenses = buildPendingExpenses(
            amount = 1000L,
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
    fun buildPendingExpenses_allowsThirtyInstallments() {
        val timeZone = TimeZone.UTC
        val firstDate = LocalDate(2026, 1, 1).atStartOfDayIn(timeZone).toEpochMilliseconds()
        var nextId = 0

        val expenses = buildPendingExpenses(
            amount = 3000L,
            firstDate = firstDate,
            installments = 30,
            categoryId = "appliances",
            description = "Installment plan",
            isShared = false,
            idProvider = { "expense-${nextId++}" },
            timeZone = timeZone
        )

        assertEquals(30, expenses.size)
        assertEquals(3000L, expenses.map { it.amount }.reduce { acc, value -> acc + value })
    }

    @Test
    fun buildRecurringMonthlyExpenses_repeatsFullAmountAcrossMonths() {
        val timeZone = TimeZone.UTC
        val firstDate = LocalDate(2026, 1, 31).atStartOfDayIn(timeZone).toEpochMilliseconds()
        var nextId = 0

        val expenses = buildRecurringMonthlyExpenses(
            amount = 1999L,
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
        assertEquals(listOf(1999L, 1999L, 1999L), expenses.map { it.amount })
        assertEquals(listOf("series-1", "series-1", "series-1"), expenses.map { it.recurringSeriesId })
        assertEquals(firstDate, expenses[0].date)
        assertEquals(LocalDate(2026, 2, 28).atStartOfDayIn(timeZone).toEpochMilliseconds(), expenses[1].date)
        assertEquals(LocalDate(2026, 3, 31).atStartOfDayIn(timeZone).toEpochMilliseconds(), expenses[2].date)
    }

    @Test
    fun buildRecurringMonthlyExpensesFromExistingExpense_keepsEditedExpenseAsFirstOccurrence() {
        val timeZone = TimeZone.UTC
        val firstDate = LocalDate(2026, 1, 16).atStartOfDayIn(timeZone).toEpochMilliseconds()
        var nextId = 0

        val expenses = buildRecurringMonthlyExpensesFromExistingExpense(
            existingExpenseId = "expense-42",
            amount = 2500L,
            firstDate = firstDate,
            categoryId = "car",
            description = "Car wash",
            isShared = false,
            recurringSeriesId = "series-42",
            idProvider = { "recurring-${nextId++}" },
            occurrences = 3,
            timeZone = timeZone
        )

        assertEquals(listOf("expense-42", "recurring-1", "recurring-2"), expenses.map { it.id })
        assertEquals(listOf("series-42", "series-42", "series-42"), expenses.map { it.recurringSeriesId })
        assertEquals(firstDate, expenses[0].date)
        assertEquals(LocalDate(2026, 2, 16).atStartOfDayIn(timeZone).toEpochMilliseconds(), expenses[1].date)
        assertEquals(LocalDate(2026, 3, 16).atStartOfDayIn(timeZone).toEpochMilliseconds(), expenses[2].date)
    }

    @Test
    fun buildRecurringMonthlyIncomes_repeatsFullAmountAcrossMonths() {
        val timeZone = TimeZone.UTC
        val firstDate = LocalDate(2026, 1, 31).atStartOfDayIn(timeZone).toEpochMilliseconds()
        var nextId = 0

        val incomes = buildRecurringMonthlyIncomes(
            amount = 3200L,
            firstDate = firstDate,
            description = "Salary",
            categoryId = null,
            recurringSeriesId = "income-series-1",
            idProvider = { "income-${nextId++}" },
            occurrences = 3,
            timeZone = timeZone
        )

        assertEquals(3, incomes.size)
        assertEquals(
            listOf(3200L, 3200L, 3200L),
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
        assertEquals(36, RECURRING_MONTHLY_OCCURRENCES)
    }

    @Test
    fun parseAmountInput_rejectsValuesOutsideLongMinorUnitRange() {
        assertEquals(null, parseAmountInput("92233720368547759.08"))
    }

    @Test
    fun parseSerializedAmount_rejectsOutOfRangeMinorUnitValues() {
        assertEquals(null, parseSerializedAmount("9223372036854775808"))
    }

    @Test
    fun exportBudgetItemsToCsv_exportsExpenseFlagsAndFiltersRange() {
        val export = buildExpensesCsvExport(
            expenses = listOf(
                expense(
                    id = "expense-in-range",
                    amount = 1234L,
                    date = LocalDate(2026, 5, 10).atStartOfDayIn(TimeZone.UTC)
                        .toEpochMilliseconds(),
                    categoryId = "default_1",
                    description = "Groceries",
                    isShared = 1L,
                    recurringSeriesId = "series-1"
                ),
                expense(
                    id = "expense-out-of-range",
                    amount = 500L,
                    date = LocalDate(2026, 6, 1).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
                    categoryId = "default_4",
                    description = null,
                    isShared = 0L,
                    recurringSeriesId = null
                )
            ),
            categories = listOf(
                Category(id = "default_1", name = "Cibo", icon = "restaurant", isArchived = 0L)
            ),
            startDate = LocalDate(2026, 5, 1),
            endDate = LocalDate(2026, 5, 31),
            localizeCategoryName = { category ->
                when (category.id) {
                    "default_1" -> "Food"
                    else -> category.name
                }
            },
            unknownCategory = "Unknown category"
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
                    amount = 320000L,
                    date = LocalDate(2026, 5, 15).atStartOfDayIn(TimeZone.UTC)
                        .toEpochMilliseconds(),
                    description = "Salary",
                    recurringSeriesId = "income-series"
                )
            ),
            categories = emptyList(),
            startDate = LocalDate(2026, 5, 1),
            endDate = LocalDate(2026, 5, 31),
            localizeCategoryName = { it.name }
        )

        assertEquals(
            "\"type\";\"date\";\"category\";\"amount\";\"description\";\"shared\";\"recurring\";\"recurring_series_id\"\n" +
                "\"income\";\"2026-05-15\";\"\";\"3200.00\";\"Salary\";\"false\";\"true\";\"income-series\"\n",
            export.content
        )
    }

    @Test
    fun parseBudgetBackup_acceptsLegacyVersionOneFiles() = runTest {
        val legacyJson = """
            {
              "format": "spesify_backup",
              "version": 1,
              "createdAtEpochMillis": 1760000000000,
              "categories": [
                {"id": "salary", "name": "Salary", "icon": "work"}
              ],
              "expenses": [],
              "incomes": [
                {"id": "income-1", "amount": "250000", "date": 1760000000000, "description": "Paycheck"}
              ]
            }
        """.trimIndent()

        val preview = parseBudgetBackup(legacyJson)

        assertEquals(1, preview.categoriesCount)
        assertEquals(0, preview.expensesCount)
        assertEquals(1, preview.incomesCount)
        assertEquals(1, preview.version)
        assertEquals(1760000000000, preview.createdAtEpochMillis)
    }

    @Test
    fun parseBudgetBackup_rejectsTamperedChecksummedFiles() = runTest {
        val tamperedJson = """
            {
              "format": "spesify_backup",
              "version": 4,
              "createdAtEpochMillis": 1760000000000,
              "checksumSha256": "0000000000000000000000000000000000000000000000000000000000000000",
              "categories": [
                {
                  "id": "food",
                  "name": "Food",
                  "icon": "restaurant",
                  "color": "#6F45E9",
                  "categoryType": "expense",
                  "isArchived": false,
                  "sortOrder": 0
                }
              ],
              "expenses": [],
              "incomes": []
            }
        """.trimIndent()

        val error = runCatching {
            parseBudgetBackup(tamperedJson)
        }.exceptionOrNull()

        assertEquals("Backup integrity check failed.", error?.message)
    }

}

private fun expense(
    id: String,
    amount: Long,
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
    amount: Long,
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
