package it.homebudget.app.data.csv.import
import it.homebudget.app.data.PendingIncome
import it.homebudget.app.data.csv.CsvImportedIncomeKey
import it.homebudget.app.data.csv.ParsedUnifiedCsvRow
import it.homebudget.app.data.csv.buildImportedIncomeId
import it.homebudget.app.data.csv.normalizeDescription
import it.homebudget.app.data.csv.registerCategoryNames
import it.homebudget.app.data.csv.resolveImportCategory
import it.homebudget.app.database.CATEGORY_TYPE_INCOME

internal object IncomeCsvRowImportHandler : CsvRowImportHandler {
    override suspend fun importRow(
        row: ParsedUnifiedCsvRow,
        rowIndex: Int,
        amount: Long,
        itemDate: Long,
        state: CsvImportState
    ): Boolean {
        val categoryId = row.categoryName
            ?.takeIf { it.isNotBlank() }
            ?.let { rawCategoryName ->
                val category = resolveImportCategory(
                    rawCategoryName = rawCategoryName,
                    categoriesByNormalizedName = state.categoriesByNormalizedName,
                    categoryType = CATEGORY_TYPE_INCOME
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
                category.id
            }
        val incomeKey = CsvImportedIncomeKey(
            date = itemDate,
            categoryId = categoryId,
            amount = amount,
            description = normalizeDescription(row.description)
        )
        if (!state.existingIncomeKeys.add(incomeKey)) {
            return false
        }

        state.incomesToInsert += PendingIncome(
            id = buildImportedIncomeId(),
            amount = amount,
            date = itemDate,
            categoryId = categoryId,
            description = row.description?.takeIf { it.isNotBlank() },
            recurringSeriesId = row.buildRecurringSeriesId(rowIndex)
        )

        return true
    }
}
