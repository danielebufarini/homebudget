package it.homebudget.app.ui.screens

import it.homebudget.app.database.Category
import it.homebudget.app.database.Expense

internal data class ExpenseRowPresentation(
    val title: String,
    val subtitleText: String,
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
        categoryIconKey = category?.icon,
        isRecurring = !expense.recurringSeriesId.isNullOrBlank()
    )
}

internal fun calendarExpenseRowPresentation(
    expense: Expense,
    categoriesById: Map<String, Category>,
    sharedLabel: String,
    selectedDateLabel: String,
    unknownCategoryLabel: String,
    resolveCategoryName: (Category) -> String
): ExpenseRowPresentation {
    val category = categoriesById[expense.categoryId]
    val categoryName = expense.categoryName(
        categoriesById = categoriesById,
        unknownCategoryLabel = unknownCategoryLabel,
        resolveCategoryName = resolveCategoryName
    )
    val title = expense.description?.takeIf { it.isNotBlank() } ?: categoryName
    val subtitleText = when {
        title != categoryName && expense.isShared == 1L -> "$categoryName • $sharedLabel"
        title != categoryName -> categoryName
        expense.isShared == 1L -> sharedLabel
        else -> selectedDateLabel
    }

    return ExpenseRowPresentation(
        title = title,
        subtitleText = subtitleText,
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
