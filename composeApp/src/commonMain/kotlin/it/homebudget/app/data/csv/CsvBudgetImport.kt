package it.homebudget.app.data.csv

import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.data.IdGenerator
import it.homebudget.app.data.PendingExpense
import it.homebudget.app.data.PendingIncome
import it.homebudget.app.data.csv.import.CsvImportState
import it.homebudget.app.data.csv.import.CsvRowImportHandlerFactory
import it.homebudget.app.data.csv.import.ImportedRecurringExpenseSeriesCompleter
import it.homebudget.app.data.csv.import.ImportedRecurringIncomeSeriesCompleter
import it.homebudget.app.data.parseAmountInput
import it.homebudget.app.database.Category
import it.homebudget.app.database.DEFAULT_CATEGORY_COLOR
import it.homebudget.app.database.Expense
import it.homebudget.app.database.Income
import it.homebudget.app.localization.loadCategoryNameResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

private val nonAlphanumericRegex = Regex("[^a-z0-9]+")

const val MAX_CSV_IMPORT_BYTES: Int = 5 * 1024 * 1024

data class CsvImportResult(
    val importedCount: Int,
    val skippedCount: Int
)

suspend fun importBudgetItemsFromCsv(
    repository: ExpenseRepository,
    csvText: String
): CsvImportResult {
    require(csvText.encodeToByteArray().size <= MAX_CSV_IMPORT_BYTES) {
        "CSV import file is too large."
    }

    repository.seedStarterCategoriesIfEmpty()
    val resolveCategoryName = loadCategoryNameResolver()

    val parsedRows = withContext(Dispatchers.Default) {
        parseUnifiedCsvRows(csvText)
    }
    if (parsedRows.isEmpty()) {
        return CsvImportResult(importedCount = 0, skippedCount = 0)
    }

    val categoriesById = repository.getAllCategoriesSnapshot()
        .associateByTo(mutableMapOf(), Category::id)

    val categoriesByNormalizedName = mutableMapOf<String, Category>()
    categoriesById.values.forEach { category ->
        registerCategoryNames(category, categoriesByNormalizedName, resolveCategoryName)
    }

    val importState = CsvImportState(
        repository = repository,
        resolveCategoryName = resolveCategoryName,
        categoriesById = categoriesById,
        categoriesByNormalizedName = categoriesByNormalizedName,
        existingExpenseKeys = repository.getAllExpensesSnapshot()
            .mapTo(mutableSetOf()) { expense -> expense.asImportKey() },
        existingIncomeKeys = repository.getAllIncomesSnapshot()
            .mapTo(mutableSetOf()) { income -> income.asImportKey() }
    )

    parsedRows.forEachIndexed { index, row ->
        val amount = parseAmountInput(row.amountText)
        if (amount == null || amount <= 0L) {
            importState.skippedCount += 1
            return@forEachIndexed
        }
        val itemDate = row.date
            .atStartOfDayIn(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()
        val imported = CsvRowImportHandlerFactory
            .create(row.type)
            .importRow(
                row = row,
                rowIndex = index,
                amount = amount,
                itemDate = itemDate,
                state = importState
            )
        if (!imported) {
            importState.skippedCount += 1
        }
    }

    val completedExpensesToInsert = ImportedRecurringExpenseSeriesCompleter().complete(
        itemsToInsert = importState.expensesToInsert,
        existingKeys = importState.existingExpenseKeys
    )

    val completedIncomesToInsert = ImportedRecurringIncomeSeriesCompleter().complete(
        itemsToInsert = importState.incomesToInsert,
        existingKeys = importState.existingIncomeKeys
    )

    if (completedExpensesToInsert.isNotEmpty()) {
        repository.insertExpenses(completedExpensesToInsert)
    }

    if (completedIncomesToInsert.isNotEmpty()) {
        repository.insertIncomes(completedIncomesToInsert)
    }

    return CsvImportResult(
        importedCount = completedExpensesToInsert.size + completedIncomesToInsert.size,
        skippedCount = importState.skippedCount
    )
}

internal data class ParsedUnifiedCsvRow(
    val type: CsvRowType,
    val date: LocalDate,
    val categoryName: String?,
    val amountText: String,
    val description: String?,
    val isShared: Boolean,
    val isRecurring: Boolean,
    val recurringSeriesId: String?
) {
    fun buildRecurringSeriesId(index: Int): String? {
        if (!isRecurring) return null
        return recurringSeriesId?.takeIf { it.isNotBlank() }
            ?: IdGenerator.newId("csv-series-${type.name.lowercase()}-$index")
    }
}

internal data class CsvImportedExpenseKey(
    val date: Long,
    val categoryId: String,
    val amount: Long,
    val description: String
)

internal data class CsvImportedIncomeKey(
    val date: Long,
    val categoryId: String?,
    val amount: Long,
    val description: String
)

internal fun registerCategoryNames(
    category: Category,
    map: MutableMap<String, Category>,
    resolveCategoryName: (String, String) -> String
) {
    map[categoryLookupKey(category.name, category.categoryType)] = category
    map[
        categoryLookupKey(
            resolveCategoryName(category.id, category.name),
            category.categoryType
        )
    ] = category
}

private fun parseUnifiedCsvRows(csvText: String): List<ParsedUnifiedCsvRow> {
    val lines = csvText
        .removePrefix("\uFEFF")
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .split('\n')
        .filter(String::isNotBlank)

    if (lines.isEmpty()) return emptyList()

    val headerColumns = parseSemicolonSeparatedRow(lines.first())
    val indices = UnifiedCsvColumnIndices.fromHeader(headerColumns) ?: return emptyList()

    return lines.subList(1, lines.size).mapNotNull { line ->
        val columns = parseSemicolonSeparatedRow(line)
        val type = columns.getOrNull(indices.typeIndex)?.trim()?.lowercase()?.toCsvRowType()
            ?: return@mapNotNull null
        val date = parseCsvDate(columns.getOrNull(indices.dateIndex)?.trim().orEmpty())
            ?: return@mapNotNull null
        val amountText = columns.getOrNull(indices.amountIndex)?.trim().orEmpty()
        if (amountText.isBlank()) return@mapNotNull null

        ParsedUnifiedCsvRow(
            type = type,
            date = date,
            categoryName = columns.getOrNull(indices.categoryIndex)?.trim()?.takeIf { it.isNotEmpty() },
            amountText = amountText,
            description = columns.getOrNull(indices.descriptionIndex)?.trim()?.takeIf { it.isNotEmpty() },
            isShared = columns.getOrNull(indices.sharedIndex)?.trim().orEmpty().toCsvBoolean(),
            isRecurring = columns.getOrNull(indices.recurringIndex)?.trim().orEmpty().toCsvBoolean(),
            recurringSeriesId = columns.getOrNull(indices.recurringSeriesIdIndex)?.trim()?.takeIf { it.isNotEmpty() }
        )
    }
}

private fun parseSemicolonSeparatedRow(line: String): List<String> {
    if (line.isEmpty()) return emptyList()

    val columns = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false
    var index = 0

    while (index < line.length) {
        when (val char = line[index]) {
            '"' if index + 1 < line.length && line[index + 1] == '"' -> {
                current.append('"')
                index += 1
            }
            '"' -> inQuotes = !inQuotes
            ';' if !inQuotes -> {
                columns += current.toString()
                current.clear()
            }
            else -> current.append(char)
        }
        index += 1
    }

    columns += current.toString()
    return columns
}

private fun parseCsvDate(value: String): LocalDate? {
    val parts = value.split('-')
    if (parts.size != 3) return null
    val (yearStr, monthStr, dayStr) = parts
    val year = yearStr.toIntOrNull() ?: return null
    val month = monthStr.toIntOrNull() ?: return null
    val day = dayStr.toIntOrNull() ?: return null

    return runCatching {
        LocalDate(
            year = year,
            monthNumber = month,
            dayOfMonth = day
        )
    }.getOrNull()
}

internal fun resolveImportCategory(
    rawCategoryName: String,
    categoriesByNormalizedName: Map<String, Category>,
    categoryType: String
): Category {
    categoriesByNormalizedName[categoryLookupKey(rawCategoryName, categoryType)]
        ?.let { return it }

    return Category(
        id = buildImportedCategoryId(),
        name = rawCategoryName.trim(),
        icon = "category",
        color = DEFAULT_CATEGORY_COLOR,
        categoryType = categoryType,
        sortOrder = 0L
    )
}

private fun normalizeCategoryToken(value: String): String =
    value.trim().lowercase().replace(nonAlphanumericRegex, " ").trim()

private fun categoryLookupKey(value: String, categoryType: String): String =
    "${categoryType.trim().lowercase()}::${normalizeCategoryToken(value)}"

internal fun normalizeDescription(value: String?): String = value?.trim()?.lowercase().orEmpty()

private fun String.toCsvBoolean(): Boolean =
    when (trim().lowercase()) {
        "true", "yes", "1" -> true
        else -> false
    }

private fun String.toCsvRowType(): CsvRowType? =
    when (this) {
        "expense" -> CsvRowType.Expense
        "income" -> CsvRowType.Income
        else -> null
    }

internal fun Expense.asImportKey() = CsvImportedExpenseKey(
    date = date,
    categoryId = categoryId,
    amount = amount,
    description = normalizeDescription(description)
)

internal fun PendingExpense.asImportKey() = CsvImportedExpenseKey(
    date = date,
    categoryId = categoryId,
    amount = amount,
    description = normalizeDescription(description)
)

internal fun Income.asImportKey() = CsvImportedIncomeKey(
    date = date,
    categoryId = categoryId,
    amount = amount,
    description = normalizeDescription(description)
)

internal fun PendingIncome.asImportKey() = CsvImportedIncomeKey(
    date = date,
    categoryId = categoryId,
    amount = amount,
    description = normalizeDescription(description)
)

private fun buildImportedId(prefix: String): String = IdGenerator.newId("csv-$prefix")

internal fun buildImportedExpenseId() = buildImportedId("expense")
internal fun buildImportedIncomeId() = buildImportedId("income")
internal fun buildImportedCategoryId() = buildImportedId("category")

private data class UnifiedCsvColumnIndices(
    val typeIndex: Int,
    val dateIndex: Int,
    val categoryIndex: Int,
    val amountIndex: Int,
    val descriptionIndex: Int,
    val sharedIndex: Int,
    val recurringIndex: Int,
    val recurringSeriesIdIndex: Int
) {
    companion object {
        fun fromHeader(columns: List<String>): UnifiedCsvColumnIndices? {
            val indexByName = buildMap {
                columns.forEachIndexed { i, col -> put(col.trim().lowercase(), i) }
            }

            return UnifiedCsvColumnIndices(
                typeIndex = indexByName["type"] ?: -1,
                dateIndex = indexByName["date"] ?: -1,
                categoryIndex = indexByName["category"] ?: -1,
                amountIndex = indexByName["amount"] ?: -1,
                descriptionIndex = indexByName["description"] ?: -1,
                sharedIndex = indexByName["shared"] ?: -1,
                recurringIndex = indexByName["recurring"] ?: -1,
                recurringSeriesIdIndex = indexByName["recurring_series_id"] ?: -1
            ).takeIf { indices ->
                indices.typeIndex >= 0 &&
                        indices.dateIndex >= 0 &&
                        indices.categoryIndex >= 0 &&
                        indices.amountIndex >= 0 &&
                        indices.descriptionIndex >= 0 &&
                        indices.sharedIndex >= 0 &&
                        indices.recurringIndex >= 0 &&
                        indices.recurringSeriesIdIndex >= 0
            }
        }
    }
}
