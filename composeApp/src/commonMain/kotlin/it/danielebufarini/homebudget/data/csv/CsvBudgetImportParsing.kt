package it.danielebufarini.homebudget.data.csv

import it.danielebufarini.homebudget.data.IdGenerator
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

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

internal fun parseUnifiedCsvRows(csvText: String): List<ParsedUnifiedCsvRow> {
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
            val normalized = columns.map { it.trim().lowercase() }

            return UnifiedCsvColumnIndices(
                typeIndex = normalized.indexOf("type"),
                dateIndex = normalized.indexOf("date"),
                categoryIndex = normalized.indexOf("category"),
                amountIndex = normalized.indexOf("amount"),
                descriptionIndex = normalized.indexOf("description"),
                sharedIndex = normalized.indexOf("shared"),
                recurringIndex = normalized.indexOf("recurring"),
                recurringSeriesIdIndex = normalized.indexOf("recurring_series_id")
            ).takeIf {
                it.typeIndex >= 0 &&
                    it.dateIndex >= 0 &&
                    it.categoryIndex >= 0 &&
                    it.amountIndex >= 0 &&
                    it.descriptionIndex >= 0 &&
                    it.sharedIndex >= 0 &&
                    it.recurringIndex >= 0 &&
                    it.recurringSeriesIdIndex >= 0
            }
        }
    }
}

private const val EXPENSE_RECURRING_SERIES_PREFIX = "recurring-expense-"
private const val INCOME_RECURRING_SERIES_PREFIX = "recurring-income-"
