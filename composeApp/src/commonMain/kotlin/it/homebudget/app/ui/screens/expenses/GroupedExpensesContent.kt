package it.homebudget.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import it.homebudget.app.data.formatAmount
import it.homebudget.app.database.Category

internal data class GroupedExpenseSectionStyle(
    val containerColor: Color,
    val contentColor: Color,
    val textStyle: TextStyle,
    val iconTint: Color?,
    val chevronContainerColor: Color?,
    val chevronContentColor: Color?
)

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
    bottomControlsBottomPadding: Dp = 16.dp
) {
    Box(modifier = modifier) {
        GroupedExpensesList(
            groupedExpenses = groupedExpenses,
            categoriesById = categoriesById,
            groupingMode = groupingMode,
            modifier = Modifier.fillMaxSize(),
            onOpenExpense = onOpenExpense,
            onDeleteExpense = onDeleteExpense,
            emptyStateText = emptyStateText,
            expenseFallbackTitle = expenseFallbackTitle,
            currencySymbol = currencySymbol,
            unknownCategoryLabel = unknownCategoryLabel,
            resolveCategoryName = resolveCategoryName,
            groupsExpandedByDefault = groupsExpandedByDefault,
            sectionStyle = sectionStyle,
            contentPadding = listContentPadding
        )

        if (showGroupingControls) {
            GroupingModeButtons(
                groupingMode = groupingMode,
                onGroupingModeChange = onGroupingModeChange,
                byCategoryLabel = byCategoryLabel,
                byDateLabel = byDateLabel,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomControlsBottomPadding)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupedExpensesList(
    groupedExpenses: List<ExpenseSection>,
    categoriesById: Map<String, Category>,
    groupingMode: ExpenseGroupingMode,
    modifier: Modifier,
    onOpenExpense: (String) -> Unit,
    onDeleteExpense: ((String) -> Unit)?,
    emptyStateText: String,
    expenseFallbackTitle: String,
    currencySymbol: String,
    unknownCategoryLabel: String,
    resolveCategoryName: (Category) -> String,
    groupsExpandedByDefault: Boolean,
    sectionStyle: GroupedExpenseSectionStyle,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val expandedState = remember { mutableStateMapOf<String, Boolean>() }

    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (groupedExpenses.isEmpty()) {
            item {
                PlatformCard {
                    Text(
                        text = emptyStateText,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            return@LazyColumn
        }

        groupedExpenses.forEach { section ->
            item(key = section.key) {
                val expanded = expandedState[section.key] ?: groupsExpandedByDefault
                val chevronRotation by animateFloatAsState(
                    targetValue = if (expanded) 180f else 0f,
                    label = "GroupedExpensesSectionChevronRotation"
                )
                PlatformCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    GroupedExpenseSectionCard(
                        section = section,
                        categoriesById = categoriesById,
                        groupingMode = groupingMode,
                        expanded = expanded,
                        chevronRotation = chevronRotation,
                        sectionStyle = sectionStyle,
                        onToggleExpanded = {
                            expandedState[section.key] = !expanded
                        },
                        onOpenExpense = onOpenExpense,
                        onDeleteExpense = onDeleteExpense,
                        expenseFallbackTitle = expenseFallbackTitle,
                        currencySymbol = currencySymbol,
                        unknownCategoryLabel = unknownCategoryLabel,
                        resolveCategoryName = resolveCategoryName
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupedExpenseSectionCard(
    section: ExpenseSection,
    categoriesById: Map<String, Category>,
    groupingMode: ExpenseGroupingMode,
    expanded: Boolean,
    chevronRotation: Float,
    sectionStyle: GroupedExpenseSectionStyle,
    onToggleExpanded: () -> Unit,
    onOpenExpense: (String) -> Unit,
    onDeleteExpense: ((String) -> Unit)?,
    expenseFallbackTitle: String,
    currencySymbol: String,
    unknownCategoryLabel: String,
    resolveCategoryName: (Category) -> String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        GroupedExpenseSectionHeader(
            section = section,
            categoriesById = categoriesById,
            groupingMode = groupingMode,
            chevronRotation = chevronRotation,
            currencySymbol = currencySymbol,
            style = sectionStyle,
            onToggleExpanded = onToggleExpanded
        )
        if (expanded) {
            HorizontalDivider()
            section.expenses.forEach { expense ->
                key(expense.id) {
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
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun GroupedExpenseSectionHeader(
    section: ExpenseSection,
    categoriesById: Map<String, Category>,
    groupingMode: ExpenseGroupingMode,
    chevronRotation: Float,
    currencySymbol: String,
    style: GroupedExpenseSectionStyle,
    onToggleExpanded: () -> Unit
) {
    val isGroupedByCategory = groupingMode == ExpenseGroupingMode.ByCategory
    val firstCategoryId = section.expenses.firstOrNull()?.categoryId
    val sectionIconKey = if (isGroupedByCategory) {
        firstCategoryId?.let(categoriesById::get)?.icon
    } else {
        null
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = style.containerColor,
        contentColor = style.contentColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpanded)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryLabel(
                iconKey = sectionIconKey,
                showIcon = isGroupedByCategory,
                colorKey = firstCategoryId.takeIf { isGroupedByCategory },
                text = section.title,
                modifier = Modifier.weight(1f),
                textStyle = style.textStyle,
                textColor = style.contentColor,
                iconTint = style.iconTint,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatAmount(section.totalAmount, currencySymbol),
                    style = style.textStyle,
                    color = style.contentColor,
                    textAlign = TextAlign.End
                )
                Spacer(modifier = Modifier.width(8.dp))
                ExpandableSectionChevron(
                    rotation = chevronRotation,
                    containerColor = style.chevronContainerColor,
                    contentColor = style.chevronContentColor
                )
            }
        }
    }
}

@Composable
private fun ExpenseRowWithOptionalDelete(
    expense: it.homebudget.app.database.Expense,
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
