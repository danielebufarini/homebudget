package it.danielebufarini.homebudget.data.csv

import it.danielebufarini.homebudget.data.ExpenseRepository
import it.danielebufarini.homebudget.data.IdGenerator
import it.danielebufarini.homebudget.data.PendingExpense
import it.danielebufarini.homebudget.data.PendingIncome
import it.danielebufarini.homebudget.data.csv.import.CsvImportState
import it.danielebufarini.homebudget.data.csv.import.CsvRowImportHandlerFactory
import it.danielebufarini.homebudget.data.csv.import.ImportedRecurringExpenseSeriesCompleter
import it.danielebufarini.homebudget.data.csv.import.ImportedRecurringIncomeSeriesCompleter
import it.danielebufarini.homebudget.data.parseAmountInput
import it.danielebufarini.homebudget.database.Category
import it.danielebufarini.homebudget.database.DEFAULT_CATEGORY_COLOR
import it.danielebufarini.homebudget.database.Expense
import it.danielebufarini.homebudget.database.Income
import it.danielebufarini.homebudget.localization.loadCategoryNameResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
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
): CsvImportResult = importBudgetItemsFromCsv(
    repository = ExpenseRepositoryCsvImportStore(repository),
    csvText = csvText
)

internal interface CsvImportStore {
    suspend fun seedStarterCategoriesIfEmpty()
    suspend fun getAllCategoriesSnapshot(): List<Category>
    suspend fun getAllExpensesSnapshot(): List<Expense>
    suspend fun getAllIncomesSnapshot(): List<Income>
    suspend fun insertCategory(
        id: String,
        name: String,
        icon: String,
        color: String,
        categoryType: String,
        isArchived: Boolean,
        sortOrder: Long
    )
    suspend fun insertExpenses(expenses: List<PendingExpense>)
    suspend fun insertIncomes(incomes: List<PendingIncome>)
}

private class ExpenseRepositoryCsvImportStore(
    private val repository: ExpenseRepository
) : CsvImportStore {
    override suspend fun seedStarterCategoriesIfEmpty() {
        repository.seedStarterCategoriesIfEmpty()
    }

    override suspend fun getAllCategoriesSnapshot(): List<Category> =
        repository.getAllCategoriesSnapshot()

    override suspend fun getAllExpensesSnapshot(): List<Expense> =
        repository.getAllExpensesSnapshot()

    override suspend fun getAllIncomesSnapshot(): List<Income> =
        repository.getAllIncomesSnapshot()

    override suspend fun insertCategory(
        id: String,
        name: String,
        icon: String,
        color: String,
        categoryType: String,
        isArchived: Boolean,
        sortOrder: Long
    ) {
        repository.insertCategory(
            id = id,
            name = name,
            icon = icon,
            color = color,
            categoryType = categoryType,
            isArchived = isArchived,
            sortOrder = sortOrder
        )
    }

    override suspend fun insertExpenses(expenses: List<PendingExpense>) {
        repository.insertExpenses(expenses)
    }

    override suspend fun insertIncomes(incomes: List<PendingIncome>) {
        repository.insertIncomes(incomes)
    }
}

internal suspend fun importBudgetItemsFromCsv(
    repository: CsvImportStore,
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
        existingExpenseRecurringOccurrenceKeys = repository.getAllExpensesSnapshot()
            .mapNotNullTo(mutableSetOf()) { expense -> expense.asRecurringOccurrenceKey() },
        existingIncomeKeys = repository.getAllIncomesSnapshot()
            .mapTo(mutableSetOf()) { income -> income.asImportKey() },
        existingIncomeRecurringOccurrenceKeys = repository.getAllIncomesSnapshot()
            .mapNotNullTo(mutableSetOf()) { income -> income.asRecurringOccurrenceKey() }
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
        existingKeys = importState.existingExpenseKeys,
        existingRecurringOccurrenceKeys = importState.existingExpenseRecurringOccurrenceKeys
    )

    val completedIncomesToInsert = ImportedRecurringIncomeSeriesCompleter().complete(
        itemsToInsert = importState.incomesToInsert,
        existingKeys = importState.existingIncomeKeys,
        existingRecurringOccurrenceKeys = importState.existingIncomeRecurringOccurrenceKeys
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
        return recurringSeriesId
            ?.takeIf { it.isNotBlank() }
            ?.normalizeRecurringSeriesIdFor(type)
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

internal data class CsvImportedRecurringOccurrenceKey(
    val recurringSeriesId: String,
    val date: Long
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
            month = Month(month),
            day = day
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

private fun String.normalizeRecurringSeriesIdFor(type: CsvRowType): String {
    return when (type) {
        CsvRowType.Expense -> replacePrefix(
            oldPrefix = INCOME_RECURRING_SERIES_PREFIX,
            newPrefix = EXPENSE_RECURRING_SERIES_PREFIX
        )
        CsvRowType.Income -> replacePrefix(
            oldPrefix = EXPENSE_RECURRING_SERIES_PREFIX,
            newPrefix = INCOME_RECURRING_SERIES_PREFIX
        )
    }
}

private fun String.replacePrefix(oldPrefix: String, newPrefix: String): String {
    return if (startsWith(oldPrefix)) {
        newPrefix + removePrefix(oldPrefix)
    } else {
        this
    }
}

internal fun Expense.asImportKey() = CsvImportedExpenseKey(
    date = date,
    categoryId = categoryId,
    amount = amount,
    description = normalizeDescription(description)
)

internal fun Expense.asRecurringOccurrenceKey(): CsvImportedRecurringOccurrenceKey? =
    recurringSeriesId.toRecurringOccurrenceKey(date)

internal fun PendingExpense.asImportKey() = CsvImportedExpenseKey(
    date = date,
    categoryId = categoryId,
    amount = amount,
    description = normalizeDescription(description)
)

internal fun PendingExpense.asRecurringOccurrenceKey(): CsvImportedRecurringOccurrenceKey? =
    recurringSeriesId.toRecurringOccurrenceKey(date)

internal fun Income.asImportKey() = CsvImportedIncomeKey(
    date = date,
    categoryId = categoryId,
    amount = amount,
    description = normalizeDescription(description)
)

internal fun Income.asRecurringOccurrenceKey(): CsvImportedRecurringOccurrenceKey? =
    recurringSeriesId.toRecurringOccurrenceKey(date)

internal fun PendingIncome.asImportKey() = CsvImportedIncomeKey(
    date = date,
    categoryId = categoryId,
    amount = amount,
    description = normalizeDescription(description)
)

internal fun PendingIncome.asRecurringOccurrenceKey(): CsvImportedRecurringOccurrenceKey? =
    recurringSeriesId.toRecurringOccurrenceKey(date)

private fun String?.toRecurringOccurrenceKey(date: Long): CsvImportedRecurringOccurrenceKey? =
    this
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.let { seriesId ->
            CsvImportedRecurringOccurrenceKey(
                recurringSeriesId = seriesId,
                date = date
            )
        }

private fun buildImportedId(prefix: String): String = IdGenerator.newId("csv-$prefix")

internal fun buildImportedExpenseId() = buildImportedId("expense")
internal fun buildImportedIncomeId() = buildImportedId("income")
internal fun buildImportedCategoryId() = buildImportedId("category")

private const val EXPENSE_RECURRING_SERIES_PREFIX = "recurring-expense-"
private const val INCOME_RECURRING_SERIES_PREFIX = "recurring-income-"

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
