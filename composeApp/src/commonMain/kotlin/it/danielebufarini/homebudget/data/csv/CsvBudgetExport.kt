package it.danielebufarini.homebudget.data.csv

import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.unknown_category
import it.danielebufarini.homebudget.data.ExpenseRepository
import it.danielebufarini.homebudget.data.formatAmountInput
import it.danielebufarini.homebudget.database.Category
import it.danielebufarini.homebudget.database.Expense
import it.danielebufarini.homebudget.database.Income
import it.danielebufarini.homebudget.localization.loadCategoryNameResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString
import kotlin.time.Instant

val CsvAllDatesStart: LocalDate = LocalDate(1970, 1, 1)
val CsvAllDatesEnd: LocalDate = LocalDate(3000, 12, 31)

enum class CsvRowType {
    Expense,
    Income
}

data class CsvExportFile(
    val fileName: String,
    val content: String
)

suspend fun exportBudgetItemsToCsv(
    repository: ExpenseRepository,
    startDate: LocalDate,
    endDate: LocalDate
): CsvExportFile {
    require(startDate <= endDate) { "startDate must be on or before endDate" }
    val resolveCategoryName = loadCategoryNameResolver()
    val (startMillis, endExclusiveMillis) = csvExportBounds(startDate, endDate)
    val expenses = repository.getExpensesSnapshotBetween(startMillis, endExclusiveMillis)
    val incomes = repository.getIncomesSnapshotBetween(startMillis, endExclusiveMillis)
    val categories = repository.getAllCategoriesSnapshot()

    return withContext(Dispatchers.Default) {
        buildFullDatabaseCsvExport(
            expenses = expenses,
            incomes = incomes,
            categories = categories,
            startDate = startDate,
            endDate = endDate,
            localizeCategoryName = { category ->
                resolveCategoryName(category.id, category.name)
            },
            unknownCategory = getString(Res.string.unknown_category)
        )
    }
}

internal fun buildExpensesCsvExport(
    expenses: List<Expense>,
    categories: List<Category>,
    startDate: LocalDate,
    endDate: LocalDate,
    localizeCategoryName: (Category) -> String,
    unknownCategory: String
): CsvExportFile {
    val categoriesById = categories.associateBy(Category::id)
    val rows = expenses
        .asSequence()
        .filterByDateRange(startDate, endDate) { it.date }
        .sortedBy(Expense::date)
        .map { expense ->
            listOf(
                "expense",
                expense.date.toCsvDate(),
                categoriesById[expense.categoryId]
                    ?.let(localizeCategoryName)
                    ?: unknownCategory,
                formatAmountInput(expense.amount),
                expense.description.orEmpty(),
                (expense.isShared == 1L).toCsvFlag(),
                (!expense.recurringSeriesId.isNullOrBlank()).toCsvFlag(),
                expense.recurringSeriesId.orEmpty()
            )
        }
        .toList()

    return CsvExportFile(
        fileName = buildCsvFileName("expenses", startDate, endDate),
        content = buildCsvContent(
            headers = listOf(
                "type",
                "date",
                "category",
                "amount",
                "description",
                "shared",
                "recurring",
                "recurring_series_id"
            ),
            rows = rows
        )
    )
}

internal fun buildIncomesCsvExport(
    incomes: List<Income>,
    categories: List<Category>,
    startDate: LocalDate,
    endDate: LocalDate,
    localizeCategoryName: (Category) -> String
): CsvExportFile {
    val categoriesById = categories.associateBy(Category::id)
    val rows = incomes
        .asSequence()
        .filterByDateRange(startDate, endDate) { it.date }
        .sortedBy(Income::date)
        .map { income ->
            listOf(
                "income",
                income.date.toCsvDate(),
                income.categoryId
                    ?.let(categoriesById::get)
                    ?.let(localizeCategoryName)
                    .orEmpty(),
                formatAmountInput(income.amount),
                income.description.orEmpty(),
                false.toCsvFlag(),
                (!income.recurringSeriesId.isNullOrBlank()).toCsvFlag(),
                income.recurringSeriesId.orEmpty()
            )
        }
        .toList()

    return CsvExportFile(
        fileName = buildCsvFileName("incomes", startDate, endDate),
        content = buildCsvContent(
            headers = listOf(
                "type",
                "date",
                "category",
                "amount",
                "description",
                "shared",
                "recurring",
                "recurring_series_id"
            ),
            rows = rows
        )
    )
}

internal fun buildFullDatabaseCsvExport(
    expenses: List<Expense>,
    incomes: List<Income>,
    categories: List<Category>,
    startDate: LocalDate,
    endDate: LocalDate,
    localizeCategoryName: (Category) -> String,
    unknownCategory: String
): CsvExportFile {
    val categoriesById = categories.associateBy(Category::id)
    val expenseRows = expenses
        .asSequence()
        .filterByDateRange(startDate, endDate) { it.date }
        .map { expense ->
            CsvExportRow(
                date = expense.date,
                values = listOf(
                    "expense",
                    expense.date.toCsvDate(),
                    categoriesById[expense.categoryId]
                        ?.let(localizeCategoryName)
                        ?: unknownCategory,
                    formatAmountInput(expense.amount),
                    expense.description.orEmpty(),
                    (expense.isShared == 1L).toCsvFlag(),
                    (!expense.recurringSeriesId.isNullOrBlank()).toCsvFlag(),
                    expense.recurringSeriesId.orEmpty()
                )
            )
        }
    val incomeRows = incomes
        .asSequence()
        .filterByDateRange(startDate, endDate) { it.date }
        .map { income ->
            CsvExportRow(
                date = income.date,
                values = listOf(
                    "income",
                    income.date.toCsvDate(),
                    income.categoryId
                        ?.let(categoriesById::get)
                        ?.let(localizeCategoryName)
                        .orEmpty(),
                    formatAmountInput(income.amount),
                    income.description.orEmpty(),
                    false.toCsvFlag(),
                    (!income.recurringSeriesId.isNullOrBlank()).toCsvFlag(),
                    income.recurringSeriesId.orEmpty()
                )
            )
        }

    val rows = (expenseRows + incomeRows)
        .sortedBy(CsvExportRow::date)
        .map(CsvExportRow::values)
        .toList()

    return CsvExportFile(
        fileName = buildCsvFileName("full_database", startDate, endDate),
        content = buildCsvContent(
            headers = listOf(
                "type",
                "date",
                "category",
                "amount",
                "description",
                "shared",
                "recurring",
                "recurring_series_id"
            ),
            rows = rows
        )
    )
}

private fun buildCsvFileName(prefix: String, startDate: LocalDate, endDate: LocalDate): String {
    if (startDate == CsvAllDatesStart && endDate == CsvAllDatesEnd) {
        return "${prefix}_all_dates.csv"
    }
    return "${prefix}_${startDate}_$endDate.csv"
}

private fun buildCsvContent(headers: List<String>, rows: List<List<String>>): String {
    return buildString {
        appendLine(headers.joinToString(";") { value -> value.toCsvCell() })
        rows.forEach { row ->
            appendLine(row.joinToString(";") { value -> value.toCsvCell() })
        }
    }
}

private fun String.toCsvCell(): String {
    val safeValue = escapeSpreadsheetFormula()
    return "\"${safeValue.replace("\"", "\"\"")}\""
}

private fun String.escapeSpreadsheetFormula(): String {
    val firstNonWhitespace = firstOrNull { !it.isWhitespace() }
    return if (firstNonWhitespace in setOf('=', '+', '-', '@')) {
        "'$this"
    } else {
        this
    }
}

private fun csvExportBounds(startDate: LocalDate, endDate: LocalDate): Pair<Long, Long> {
    val timeZone = TimeZone.currentSystemDefault()
    val startMillis = startDate.atStartOfDayIn(timeZone).toEpochMilliseconds()
    val endExclusiveMillis = endDate
        .plus(1, DateTimeUnit.DAY)
        .atStartOfDayIn(timeZone)
        .toEpochMilliseconds()
    return startMillis to endExclusiveMillis
}

private fun Boolean.toCsvFlag(): String = toString()

private fun <T> Sequence<T>.filterByDateRange(
    startDate: LocalDate,
    endDate: LocalDate,
    dateSelector: (T) -> Long
): Sequence<T> {
    return filter { item ->
        val date = dateSelector(item).toLocalDate()
        date in startDate..endDate
    }
}

private fun Long.toLocalDate(): LocalDate {
    return Instant.fromEpochMilliseconds(this)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
}

private fun Long.toCsvDate(): String = toLocalDate().toString()

private data class CsvExportRow(
    val date: Long,
    val values: List<String>
)
