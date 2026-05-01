package it.homebudget.app.data

import com.ionspin.kotlin.bignum.integer.BigInteger
import it.homebudget.app.database.Category
import it.homebudget.app.database.Expense
import it.homebudget.app.database.Income
import it.homebudget.app.localization.AppStrings
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.random.Random
import kotlin.time.Clock

private val nonAlphanumericRegex = Regex("[^a-z0-9]+")

data class CsvImportResult(
    val importedCount: Int,
    val skippedCount: Int
)

suspend fun importBudgetItemsFromCsv(
    repository: ExpenseRepository,
    csvText: String
): CsvImportResult {
    repository.insertDefaultCategoriesIfEmpty()

    val parsedRows = parseUnifiedCsvRows(csvText)
    if (parsedRows.isEmpty()) {
        return CsvImportResult(importedCount = 0, skippedCount = 0)
    }

    val categoriesById = repository.getAllCategoriesSnapshot()
        .associateByTo(mutableMapOf(), Category::id)

    val categoriesByNormalizedName = mutableMapOf<String, Category>()
    categoriesById.values.forEach { category ->
        registerCategoryNames(category, categoriesByNormalizedName)
    }

    val existingExpenseKeys = repository.getAllExpensesSnapshot()
        .mapTo(mutableSetOf()) { it.asImportKey() }
    val existingIncomeKeys = repository.getAllIncomesSnapshot()
        .mapTo(mutableSetOf()) { it.asImportKey() }

    val expensesToInsert = mutableListOf<PendingExpense>()
    val incomesToInsert = mutableListOf<PendingIncome>()
    var skippedCount = 0

    parsedRows.forEachIndexed { index, row ->
        val amount = parseAmountInput(row.amountText)
        if (amount == null || amount <= BigInteger.ZERO) {
            skippedCount += 1
            return@forEachIndexed
        }

        val itemDate = row.date.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()

        when (row.type) {
            CsvRowType.Expense -> {
                val rawCategoryName = row.categoryName
                if (rawCategoryName.isNullOrBlank()) {
                    skippedCount += 1
                    return@forEachIndexed
                }

                val category = resolveImportCategory(
                    rawCategoryName = rawCategoryName,
                    categoriesByNormalizedName = categoriesByNormalizedName
                )

                if (categoriesById[category.id] == null) {
                    repository.insertCategory(
                        id = category.id,
                        name = category.name,
                        icon = category.icon,
                        isCustom = category.isCustom == 1L
                    )
                    categoriesById[category.id] = category
                    registerCategoryNames(category, categoriesByNormalizedName)
                }

                val expenseKey = CsvImportedExpenseKey(
                    date = itemDate,
                    categoryId = category.id,
                    amount = amount,
                    description = normalizeDescription(row.description)
                )
                if (!existingExpenseKeys.add(expenseKey)) {
                    skippedCount += 1
                    return@forEachIndexed
                }

                expensesToInsert += PendingExpense(
                    id = buildImportedExpenseId(),
                    amount = amount,
                    date = itemDate,
                    categoryId = category.id,
                    description = row.description?.takeIf { it.isNotBlank() },
                    isShared = row.isShared,
                    recurringSeriesId = row.buildRecurringSeriesId(index)
                )
            }

            CsvRowType.Income -> {
                val incomeKey = CsvImportedIncomeKey(
                    date = itemDate,
                    amount = amount,
                    description = normalizeDescription(row.description)
                )
                if (!existingIncomeKeys.add(incomeKey)) {
                    skippedCount += 1
                    return@forEachIndexed
                }

                incomesToInsert += PendingIncome(
                    id = buildImportedIncomeId(),
                    amount = amount,
                    date = itemDate,
                    description = row.description?.takeIf { it.isNotBlank() },
                    recurringSeriesId = row.buildRecurringSeriesId(index)
                )
            }
        }
    }

    if (expensesToInsert.isNotEmpty()) repository.insertExpenses(expensesToInsert)
    if (incomesToInsert.isNotEmpty()) repository.insertIncomes(incomesToInsert)

    return CsvImportResult(
        importedCount = expensesToInsert.size + incomesToInsert.size,
        skippedCount = skippedCount
    )
}

private fun registerCategoryNames(category: Category, map: MutableMap<String, Category>) {
    map[normalizeCategoryToken(category.name)] = category
    map[normalizeCategoryToken(AppStrings.categoryName(category.id, category.name, category.isCustom))] = category
}

private data class ParsedUnifiedCsvRow(
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
            ?: "csv-series-${type.name.lowercase()}-${Clock.System.now().toEpochMilliseconds()}-$index"
    }
}

private data class CsvImportedExpenseKey(
    val date: Long,
    val categoryId: String,
    val amount: BigInteger,
    val description: String
)

private data class CsvImportedIncomeKey(
    val date: Long,
    val amount: BigInteger,
    val description: String
)

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
    return runCatching { LocalDate(year = year, month = month, day = day) }.getOrNull()
}

private fun resolveImportCategory(
    rawCategoryName: String,
    categoriesByNormalizedName: Map<String, Category>
): Category {
    val normalizedName = normalizeCategoryToken(rawCategoryName)
    categoriesByNormalizedName[normalizedName]?.let { return it }

    return Category(
        id = buildImportedCategoryId(),
        name = rawCategoryName.trim(),
        icon = "category",
        isCustom = 1L
    )
}

private fun normalizeCategoryToken(value: String): String =
    value.trim().lowercase().replace(nonAlphanumericRegex, " ").trim()

private fun normalizeDescription(value: String?): String = value?.trim()?.lowercase().orEmpty()

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

private fun Expense.asImportKey() = CsvImportedExpenseKey(
    date = date,
    categoryId = categoryId,
    amount = amount,
    description = normalizeDescription(description)
)

private fun Income.asImportKey() = CsvImportedIncomeKey(
    date = date,
    amount = amount,
    description = normalizeDescription(description)
)

private fun buildImportedId(prefix: String): String =
    "csv-${prefix}_${Clock.System.now().toEpochMilliseconds()}_${Random.nextInt(1_000, 9_999)}"

private fun buildImportedExpenseId() = buildImportedId("expense")
private fun buildImportedIncomeId() = buildImportedId("income")
private fun buildImportedCategoryId() = buildImportedId("category")

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