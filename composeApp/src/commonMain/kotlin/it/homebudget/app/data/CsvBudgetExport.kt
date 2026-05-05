package it.homebudget.app.data

import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.unknown_category
import it.homebudget.app.database.Category
import it.homebudget.app.database.Expense
import it.homebudget.app.database.Income
import it.homebudget.app.localization.loadCategoryNameResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
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
    val expenses = repository.getAllExpensesSnapshot()
    val incomes = repository.getAllIncomesSnapshot()
    val categories = repository.getAllCategoriesSnapshot()

    return withContext(Dispatchers.Default) {
        buildFullDatabaseCsvExport(
            expenses = expenses,
            incomes = incomes,
            categories = categories,
            startDate = startDate,
            endDate = endDate,
            localizeCategoryName = { category ->
                resolveCategoryName(category.id, category.name, category.isCustom)
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
    startDate: LocalDate,
    endDate: LocalDate
): CsvExportFile {
    val rows = incomes
        .asSequence()
        .filterByDateRange(startDate, endDate) { it.date }
        .sortedBy(Income::date)
        .map { income ->
            listOf(
                "income",
                income.date.toCsvDate(),
                "",
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
                    "",
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
    return "\"${replace("\"", "\"\"")}\""
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
