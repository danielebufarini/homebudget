package it.danielebufarini.spesify.ui.screens.expenses

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import it.danielebufarini.spesify.data.formatAmount
import it.danielebufarini.spesify.database.Category
import it.danielebufarini.spesify.database.Expense
import it.danielebufarini.spesify.ui.screens.ExpenseGroupingMode
import it.danielebufarini.spesify.ui.screens.ExpenseSection
import it.danielebufarini.spesify.ui.screens.GroupedTransactionListContent
import it.danielebufarini.spesify.ui.screens.GroupedTransactionSection
import it.danielebufarini.spesify.ui.screens.GroupedTransactionSectionStyle

internal typealias GroupedExpenseSectionStyle = GroupedTransactionSectionStyle

@Composable
internal fun GroupedExpensesContent(
    groupedExpenses: List<ExpenseSection>,
    categoriesById: Map<String, Category>,
    modifier: Modifier,
    groupingMode: ExpenseGroupingMode,
    onGroupingModeChange: (ExpenseGroupingMode) -> Unit,
    onOpenExpense: (String) -> Unit,
    onDeleteExpense: ((String) -> Unit)?,
    emptyStateText: String,
    expenseFallbackTitle: String,
    currencySymbol: String,
    unknownCategoryLabel: String,
    resolveCategoryName: (Category) -> String,
    byCategoryLabel: String,
    byDateLabel: String,
    groupsExpandedByDefault: Boolean,
    sectionStyle: GroupedExpenseSectionStyle,
    showGroupingControls: Boolean = true,
    listContentPadding: PaddingValues = PaddingValues(0.dp),
    bottomControlsBottomPadding: Dp = 16.dp,
    loadMoreSearchResultsLabel: String = "",
    canLoadMoreSearchResults: Boolean = false,
    onLoadMoreSearchResults: () -> Unit = {},
    isLoading: Boolean = false
) {
    GroupedTransactionListContent(
        sections = groupedExpenses.map { section ->
            val firstCategoryId = section.expenses.firstOrNull()?.categoryId
            GroupedTransactionSection(
                key = section.key,
                title = section.title,
                totalAmount = section.totalAmount,
                categoryId = firstCategoryId,
                categoryIconKey = firstCategoryId?.let(categoriesById::get)?.icon,
                items = section.expenses
            )
        },
        modifier = modifier,
        groupingMode = groupingMode,
        onGroupingModeChange = onGroupingModeChange,
        emptyStateText = emptyStateText,
        currencySymbol = currencySymbol,
        byCategoryLabel = byCategoryLabel,
        byDateLabel = byDateLabel,
        groupsExpandedByDefault = groupsExpandedByDefault,
        sectionStyle = sectionStyle,
        showGroupingControls = showGroupingControls,
        listContentPadding = listContentPadding,
        bottomControlsBottomPadding = bottomControlsBottomPadding,
        loadMoreSearchResultsLabel = loadMoreSearchResultsLabel,
        canLoadMoreSearchResults = canLoadMoreSearchResults,
        onLoadMoreSearchResults = onLoadMoreSearchResults,
        isLoading = isLoading,
        itemKey = Expense::id
    ) { expense ->
        val row = groupedExpenseRowPresentation(
            expense = expense,
            categoriesById = categoriesById,
            isGroupedByDate = groupingMode == ExpenseGroupingMode.ByDate,
            expenseFallbackTitle = expenseFallbackTitle,
            unknownCategoryLabel = unknownCategoryLabel,
            resolveCategoryName = resolveCategoryName
        )
        ExpenseRowWithOptionalDelete(
            expense = expense,
            row = row,
            amountText = formatAmount(expense.amount, currencySymbol),
            onOpenExpense = onOpenExpense,
            onDeleteExpense = onDeleteExpense
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseRowWithOptionalDelete(
    expense: Expense,
    row: ExpenseRowPresentation,
    amountText: String,
    onOpenExpense: (String) -> Unit,
    onDeleteExpense: ((String) -> Unit)?
) {
    val content: @Composable () -> Unit = {
        ExpenseListItemRow(
            title = row.title,
            subtitleText = row.subtitleText,
            amountText = amountText,
            categoryColorKey = row.categoryColorKey,
            categoryIconKey = row.categoryIconKey,
            isRecurring = row.isRecurring,
            onClick = { onOpenExpense(expense.id) }
        )
    }

    if (onDeleteExpense == null) {
        content()
    } else {
        SwipeToDismissBox(
            state = rememberExpenseSwipeToDeleteBoxState(
                itemId = expense.id,
                onDeleteExpense = onDeleteExpense
            ),
            enableDismissFromStartToEnd = false,
            backgroundContent = { DeleteExpenseBackground() },
            content = { content() }
        )
    }
}
