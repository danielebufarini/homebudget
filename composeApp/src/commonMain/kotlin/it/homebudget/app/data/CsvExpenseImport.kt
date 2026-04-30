package it.homebudget.app.data

import com.ionspin.kotlin.bignum.integer.BigInteger
import it.homebudget.app.database.Category
import it.homebudget.app.database.Expense
import it.homebudget.app.localization.AppStrings
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.random.Random
import kotlin.time.Clock

data class CsvImportResult(
    val importedCount: Int,
    val skippedCount: Int
)

suspend fun importExpensesFromCsv(
    repository: ExpenseRepository,
    csvText: String
): CsvImportResult {
    repository.insertDefaultCategoriesIfEmpty()

    val rows = parseCsvExpenseRows(csvText)
    if (rows.isEmpty()) {
        return CsvImportResult(
            importedCount = 0,
            skippedCount = 0
        )
    }

    val categories = repository.getAllCategoriesSnapshot().toMutableList()
    val categoriesById = mutableMapOf<String, Category>()
    val categoriesByNormalizedName = mutableMapOf<String, Category>()

    fun registerCategory(category: Category) {
        categoriesById[category.id] = category
        categoriesByNormalizedName[normalizeCategoryToken(category.name)] = category
        categoriesByNormalizedName[
            normalizeCategoryToken(
                AppStrings.categoryName(category.id, category.name, category.isCustom)
            )
        ] = category
    }

    categories.forEach(::registerCategory)

    val existingExpenseKeys = repository.getAllExpensesSnapshot()
        .mapTo(mutableSetOf()) { expense -> expense.asImportKey() }

    val expensesToInsert = mutableListOf<PendingExpense>()
    var skippedCount = 0

    rows.forEach { row ->
        val amount = parseAmountInput(row.amountText)
        if (amount == null || amount <= BigInteger.ZERO) {
            skippedCount += 1
            return@forEach
        }

        val category = resolveImportCategory(
            rawCategoryName = row.categoryName,
            categoriesById = categoriesById,
            categoriesByNormalizedName = categoriesByNormalizedName
        ) ?: run {
            skippedCount += 1
            return@forEach
        }

        if (categoriesById[category.id] == null) {
            repository.insertCategory(
                id = category.id,
                name = category.name,
                icon = category.icon,
                isCustom = category.isCustom == 1L
            )
            registerCategory(category)
        }

        val description = row.notes
            ?.trim()
            ?.takeIf { note ->
                note.isNotEmpty() && !note.equals("Recurring expense", ignoreCase = true)
            }
        val expenseKey = CsvImportedExpenseKey(
            date = row.date.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds(),
            categoryId = category.id,
            amount = amount,
            description = normalizeDescription(description)
        )

        if (!existingExpenseKeys.add(expenseKey)) {
            skippedCount += 1
            return@forEach
        }

        expensesToInsert += PendingExpense(
            id = buildImportedExpenseId(),
            amount = amount,
            date = expenseKey.date,
            categoryId = category.id,
            description = description,
            isShared = false,
            recurringSeriesId = null
        )
    }

    if (expensesToInsert.isNotEmpty()) {
        repository.insertExpenses(expensesToInsert)
    }

    return CsvImportResult(
        importedCount = expensesToInsert.size,
        skippedCount = skippedCount
    )
}

private data class ParsedCsvExpenseRow(
    val date: LocalDate,
    val categoryName: String,
    val amountText: String,
    val notes: String?
)

private data class CsvImportedExpenseKey(
    val date: Long,
    val categoryId: String,
    val amount: BigInteger,
    val description: String
)

private fun parseCsvExpenseRows(csvText: String): List<ParsedCsvExpenseRow> {
    val lines = csvText
        .removePrefix("\uFEFF")
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .split('\n')
        .filter { line -> line.isNotBlank() }

    if (lines.isEmpty()) {
        return emptyList()
    }

    val headerColumns = parseSemicolonSeparatedRow(lines.first())
    val indices = CsvColumnIndices.fromHeader(headerColumns) ?: return emptyList()

    return lines
        .drop(1)
        .mapNotNull { line ->
            val columns = parseSemicolonSeparatedRow(line)
            val dateText = columns.getOrNull(indices.dateIndex)?.trim().orEmpty()
            val categoryText = columns.getOrNull(indices.categoryIndex)?.trim().orEmpty()
            val amountText = columns.getOrNull(indices.amountIndex)?.trim().orEmpty()
            val notesText = columns.getOrNull(indices.notesIndex)?.trim()

            val date = parseCsvDate(dateText) ?: return@mapNotNull null
            if (categoryText.isBlank() || amountText.isBlank()) {
                return@mapNotNull null
            }

            ParsedCsvExpenseRow(
                date = date,
                categoryName = categoryText,
                amountText = amountText,
                notes = notesText
            )
        }
}

private fun parseSemicolonSeparatedRow(line: String): List<String> {
    if (line.isEmpty()) {
        return emptyList()
    }

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
            '"' -> {
                inQuotes = !inQuotes
            }
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
    if (parts.size != 3) {
        return null
    }

    val year = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    val day = parts[2].toIntOrNull() ?: return null

    return runCatching {
        LocalDate(
            year = year,
            month = month,
            day = day
        )
    }.getOrNull()
}

private fun resolveImportCategory(
    rawCategoryName: String,
    categoriesById: Map<String, Category>,
    categoriesByNormalizedName: Map<String, Category>
): Category? {
    val trimmedName = rawCategoryName.trim()
    if (trimmedName.isEmpty()) {
        return null
    }

    val normalizedName = normalizeCategoryToken(trimmedName)
    categoriesByNormalizedName[normalizedName]?.let { return it }

    importedCategoryAliasTargets[normalizedName]
        ?.let { categoryId -> categoriesById[categoryId] }
        ?.let { return it }

    return Category(
        id = buildImportedCategoryId(),
        name = trimmedName,
        icon = "category",
        isCustom = 1L
    )
}

private fun normalizeCategoryToken(value: String): String {
    return value
        .trim()
        .lowercase()
        .replace(categoryTokenSeparatorRegex, " ")
        .replace("\\s+".toRegex(), " ")
        .trim()
}

private fun normalizeDescription(value: String?): String {
    return value
        ?.trim()
        ?.lowercase()
        .orEmpty()
}

private fun Expense.asImportKey(): CsvImportedExpenseKey {
    return CsvImportedExpenseKey(
        date = date,
        categoryId = categoryId,
        amount = amount,
        description = normalizeDescription(description)
    )
}

private fun buildImportedExpenseId(): String {
    return "csv-expense_${Clock.System.now().toEpochMilliseconds()}_${Random.nextInt(1_000, 9_999)}"
}

private fun buildImportedCategoryId(): String {
    return "csv-category_${Clock.System.now().toEpochMilliseconds()}_${Random.nextInt(1_000, 9_999)}"
}

private val categoryTokenSeparatorRegex = "[^a-z0-9]+".toRegex()

private val importedCategoryAliasTargets = mapOf(
    "home rent" to "default_0",
    "home" to "default_0",
    "rent" to "default_0",
    "food groceries" to "default_1",
    "groceries" to "default_1",
    "food" to "default_1",
    "utilities" to "default_2",
    "bills" to "default_2",
    "auto transport" to "default_3",
    "car transport" to "default_3",
    "transport" to "default_3",
    "departmental" to "default_4",
    "miscellaneous" to "default_4",
    "misc" to "default_4",
    "various" to "default_4"
)

private data class CsvColumnIndices(
    val dateIndex: Int,
    val categoryIndex: Int,
    val amountIndex: Int,
    val notesIndex: Int
) {
    companion object {
        fun fromHeader(columns: List<String>): CsvColumnIndices? {
            val normalizedColumns = columns.map { normalizeCategoryToken(it) }
            val dateIndex = normalizedColumns.indexOf("date")
            val categoryIndex = normalizedColumns.indexOf("category")
            val amountIndex = normalizedColumns.indexOf("expense amount")
            val notesIndex = normalizedColumns.indexOf("notes")

            if (dateIndex == -1 || categoryIndex == -1 || amountIndex == -1 || notesIndex == -1) {
                return null
            }

            return CsvColumnIndices(
                dateIndex = dateIndex,
                categoryIndex = categoryIndex,
                amountIndex = amountIndex,
                notesIndex = notesIndex
            )
        }
    }
}
