package it.danielebufarini.spesify.data.csv

import it.danielebufarini.spesify.data.ExpenseRepository
import it.danielebufarini.spesify.data.IdGenerator
import it.danielebufarini.spesify.data.PendingExpense
import it.danielebufarini.spesify.data.PendingIncome
import it.danielebufarini.spesify.data.csv.import.CsvImportState
import it.danielebufarini.spesify.data.csv.import.CsvRowImportHandlerFactory
import it.danielebufarini.spesify.data.csv.import.ImportedRecurringExpenseSeriesCompleter
import it.danielebufarini.spesify.data.csv.import.ImportedRecurringIncomeSeriesCompleter
import it.danielebufarini.spesify.data.parseAmountInput
import it.danielebufarini.spesify.database.Category
import it.danielebufarini.spesify.database.DEFAULT_CATEGORY_COLOR
import it.danielebufarini.spesify.database.Expense
import it.danielebufarini.spesify.database.Income
import it.danielebufarini.spesify.localization.loadCategoryNameResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    val importTimeZone = TimeZone.currentSystemDefault()
    val categoriesById = repository.getAllCategoriesSnapshot()
        .associateByTo(mutableMapOf(), Category::id)
    val existingExpenses = repository.getAllExpensesSnapshot()
    val existingIncomes = repository.getAllIncomesSnapshot()

    val categoriesByNormalizedName = mutableMapOf<String, Category>()
    categoriesById.values.forEach { category ->
        registerCategoryNames(category, categoriesByNormalizedName, resolveCategoryName)
    }

    val importState = CsvImportState(
        repository = repository,
        resolveCategoryName = resolveCategoryName,
        categoriesById = categoriesById,
        categoriesByNormalizedName = categoriesByNormalizedName,
        existingExpenseKeys = existingExpenses
            .mapTo(mutableSetOf()) { expense -> expense.asImportKey() },
        existingExpenseRecurringOccurrenceKeys = existingExpenses
            .mapNotNullTo(mutableSetOf()) { expense -> expense.asRecurringOccurrenceKey() },
        existingIncomeKeys = existingIncomes
            .mapTo(mutableSetOf()) { income -> income.asImportKey() },
        existingIncomeRecurringOccurrenceKeys = existingIncomes
            .mapNotNullTo(mutableSetOf()) { income -> income.asRecurringOccurrenceKey() }
    )

    parsedRows.forEachIndexed { index, row ->
        val amount = parseAmountInput(row.amountText)
        if (amount == null || amount <= 0L) {
            importState.skippedCount += 1
            return@forEachIndexed
        }
        val itemDate = row.date
            .atStartOfDayIn(importTimeZone)
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
