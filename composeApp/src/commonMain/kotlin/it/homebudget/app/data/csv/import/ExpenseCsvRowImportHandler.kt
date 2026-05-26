package it.homebudget.app.data.csv.import
import it.homebudget.app.data.PendingExpense
import it.homebudget.app.data.csv.CsvImportedExpenseKey
import it.homebudget.app.data.csv.CsvImportedRecurringOccurrenceKey
import it.homebudget.app.data.csv.ParsedUnifiedCsvRow
import it.homebudget.app.data.csv.buildImportedExpenseId
import it.homebudget.app.data.csv.normalizeDescription
import it.homebudget.app.data.csv.registerCategoryNames
import it.homebudget.app.data.csv.resolveImportCategory
import it.homebudget.app.database.CATEGORY_TYPE_EXPENSE

internal object ExpenseCsvRowImportHandler : CsvRowImportHandler {
    override suspend fun importRow(
        row: ParsedUnifiedCsvRow,
        rowIndex: Int,
        amount: Long,
        itemDate: Long,
        state: CsvImportState
    ): Boolean {
        val rawCategoryName = row.categoryName
        if (rawCategoryName.isNullOrBlank()) {
            return false
        }

        val category = resolveImportCategory(
            rawCategoryName = rawCategoryName,
            categoriesByNormalizedName = state.categoriesByNormalizedName,
            categoryType = CATEGORY_TYPE_EXPENSE
        )

        if (state.categoriesById[category.id] == null) {
            state.repository.insertCategory(
                id = category.id,
                name = category.name,
                icon = category.icon,
                color = category.color,
                categoryType = category.categoryType,
                isArchived = category.isArchived == 1L,
                sortOrder = category.sortOrder
            )
            state.categoriesById[category.id] = category
            registerCategoryNames(
                category = category,
                map = state.categoriesByNormalizedName,
                resolveCategoryName = state.resolveCategoryName
            )
        }

        val recurringSeriesId = row.buildRecurringSeriesId(rowIndex)
        val recurringOccurrenceKey = recurringSeriesId?.let { seriesId ->
            CsvImportedRecurringOccurrenceKey(
                recurringSeriesId = seriesId,
                date = itemDate
            )
        }
        if (
            recurringOccurrenceKey != null &&
            !state.existingExpenseRecurringOccurrenceKeys.add(recurringOccurrenceKey)
        ) {
            return false
        }

        val expenseKey = CsvImportedExpenseKey(
            date = itemDate,
            categoryId = category.id,
            amount = amount,
            description = normalizeDescription(row.description)
        )
        if (!state.existingExpenseKeys.add(expenseKey)) {
            recurringOccurrenceKey?.let(state.existingExpenseRecurringOccurrenceKeys::remove)
            return false
        }

        state.expensesToInsert += PendingExpense(
            id = buildImportedExpenseId(),
            amount = amount,
            date = itemDate,
            categoryId = category.id,
            description = row.description?.takeIf { it.isNotBlank() },
            isShared = row.isShared,
            recurringSeriesId = recurringSeriesId
        )

        return true
    }
}
