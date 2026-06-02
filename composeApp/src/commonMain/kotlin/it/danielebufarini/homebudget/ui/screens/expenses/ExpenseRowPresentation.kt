package it.danielebufarini.homebudget.ui.screens.expenses

import it.danielebufarini.homebudget.database.Category
import it.danielebufarini.homebudget.database.Expense

internal data class ExpenseRowPresentation(
    val title: String,
    val subtitleText: String,
    val categoryColorKey: String?,
    val categoryIconKey: String?,
    val isRecurring: Boolean
)

internal fun groupedExpenseRowPresentation(
    expense: Expense,
    categoriesById: Map<String, Category>,
    isGroupedByDate: Boolean,
    expenseFallbackTitle: String,
    unknownCategoryLabel: String,
    resolveCategoryName: (Category) -> String
): ExpenseRowPresentation {
    val expenseName = expense.displayName(expenseFallbackTitle)
    val category = categoriesById[expense.categoryId]
    val categoryName = expense.categoryName(
        categoriesById = categoriesById,
        unknownCategoryLabel = unknownCategoryLabel,
        resolveCategoryName = resolveCategoryName
    )

    return ExpenseRowPresentation(
        title = if (isGroupedByDate) categoryName else expenseName,
        subtitleText = if (isGroupedByDate) expenseName else formatExpenseDate(expense.date),
        categoryColorKey = category?.id,
        categoryIconKey = category?.icon,
        isRecurring = !expense.recurringSeriesId.isNullOrBlank()
    )
}

private fun Expense.displayName(fallbackTitle: String): String {
    return description?.ifBlank { fallbackTitle } ?: fallbackTitle
}

private fun Expense.categoryName(
    categoriesById: Map<String, Category>,
    unknownCategoryLabel: String,
    resolveCategoryName: (Category) -> String
): String {
    return categoriesById[categoryId]
        ?.let(resolveCategoryName)
        ?: unknownCategoryLabel
}
