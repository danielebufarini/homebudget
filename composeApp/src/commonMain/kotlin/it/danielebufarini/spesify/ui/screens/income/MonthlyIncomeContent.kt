package it.danielebufarini.spesify.ui.screens.income

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.danielebufarini.spesify.data.formatAmount
import it.danielebufarini.spesify.database.Category
import it.danielebufarini.spesify.database.Income
import it.danielebufarini.spesify.ui.screens.ExpenseGroupingMode
import it.danielebufarini.spesify.ui.screens.GroupedTransactionListContent
import it.danielebufarini.spesify.ui.screens.GroupedTransactionSection
import it.danielebufarini.spesify.ui.screens.GroupedTransactionSectionStyle
import it.danielebufarini.spesify.ui.screens.IncomeSection
import it.danielebufarini.spesify.ui.screens.common.MonthCursor
import it.danielebufarini.spesify.ui.screens.common.MonthNavigationTitle
import it.danielebufarini.spesify.ui.screens.edgeToEdgeListContentPadding
import it.danielebufarini.spesify.ui.screens.expenses.DeleteExpenseBackground
import it.danielebufarini.spesify.ui.screens.expenses.ExpenseListItemRow
import it.danielebufarini.spesify.ui.screens.expenses.epochMillisToLocalDate
import it.danielebufarini.spesify.ui.screens.expenses.formatExpenseDateGroupTitle
import it.danielebufarini.spesify.ui.screens.monthSwipeNavigation
import it.danielebufarini.spesify.ui.screens.transactions.BottomTransactionQuickActions
import it.danielebufarini.spesify.ui.screens.transactions.TransactionDeleteConfirmationDialog
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import spesify.composeapp.generated.resources.Res
import spesify.composeapp.generated.resources.currency_symbol
import spesify.composeapp.generated.resources.income

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MonthlyIncomeTopBar(
    selectedMonth: MonthCursor,
    totalAmount: Long,
    strings: MonthlyIncomeStrings,
    isIos: Boolean,
    onBack: () -> Unit,
    onAddIncome: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    CenterAlignedTopAppBar(
        title = {
            MonthNavigationTitle(
                selectedMonth = selectedMonth,
                subtitle = "${strings.income} • ${formatAmount(totalAmount, strings.currencySymbol)}",
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth
            )
        },
        navigationIcon = {
            if (isIos) {
                TextButton(onClick = onBack) {
                    Text(strings.back)
                }
            }
        },
        actions = {
            if (!isIos) {
                BottomTransactionQuickActions(
                    addContentDescription = strings.addIncome,
                    onAddTransaction = onAddIncome,
                    modifier = Modifier.padding(end = 12.dp)
                )
            }
        }
    )
}

@Composable
internal fun MonthlyIncomeContent(
    padding: PaddingValues,
    sections: List<GroupedTransactionSection<Income>>,
    categoriesById: Map<String, Category>,
    groupingMode: ExpenseGroupingMode,
    onGroupingModeChange: (ExpenseGroupingMode) -> Unit,
    showNavigationChrome: Boolean,
    isIos: Boolean,
    strings: MonthlyIncomeStrings,
    canLoadMoreSearchResults: Boolean,
    onLoadMoreSearchResults: () -> Unit,
    isLoading: Boolean,
    onOpenIncome: (String) -> Unit,
    onDeleteIncome: (String) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    val showFloatingBottomControls = !isIos
    val bottomControlClearance = if (showFloatingBottomControls) 88.dp else 0.dp
    val listContentPadding = edgeToEdgeListContentPadding(
        scaffoldPadding = padding,
        bottom = 16.dp + bottomControlClearance
    )
    val bottomControlsPadding = padding.calculateBottomPadding() + 16.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .monthSwipeNavigation(
                enabled = showNavigationChrome,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth
            )
    ) {
        GroupedTransactionListContent(
            sections = sections,
            modifier = Modifier.fillMaxSize(),
            groupingMode = groupingMode,
            onGroupingModeChange = onGroupingModeChange,
            emptyStateText = strings.noIncomeForMonth,
            currencySymbol = strings.currencySymbol,
            byCategoryLabel = strings.byCategory,
            byDateLabel = strings.byDate,
            groupsExpandedByDefault = false,
            sectionStyle = monthlyIncomeSectionStyle(),
            showGroupingControls = showFloatingBottomControls,
            listContentPadding = listContentPadding,
            bottomControlsBottomPadding = bottomControlsPadding,
            loadMoreSearchResultsLabel = strings.loadMoreSearchResults,
            canLoadMoreSearchResults = canLoadMoreSearchResults,
            onLoadMoreSearchResults = onLoadMoreSearchResults,
            isLoading = isLoading,
            emptyStateCentered = true,
            itemKey = Income::id
        ) { income ->
            val category = income.categoryId?.let(categoriesById::get)
            MonthlyIncomeRow(
                income = income,
                categoryIconKey = category?.icon,
                categoryColorKey = income.categoryId,
                onOpenIncome = onOpenIncome,
                onDeleteIncome = onDeleteIncome
            )
        }
    }
}

@Composable
internal fun monthlyIncomeSectionStyle(): GroupedTransactionSectionStyle =
    GroupedTransactionSectionStyle(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        textStyle = MaterialTheme.typography.titleMedium,
        iconTint = null,
        chevronContainerColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.16f),
        chevronContentColor = MaterialTheme.colorScheme.onPrimaryContainer
    )

@Composable
internal fun IncomeDeleteDialog(
    income: Income,
    strings: MonthlyIncomeStrings,
    onDeleteItem: () -> Unit,
    onDeleteSeries: (String) -> Unit,
    onDismiss: () -> Unit
) {
    TransactionDeleteConfirmationDialog(
        itemDisplayName = income.description?.ifBlank { strings.income } ?: strings.income,
        recurringSeriesId = income.recurringSeriesId,
        deleteTitle = strings.delete,
        deleteItemConfirmationMessageTemplate = strings.deleteItemConfirmationMessageTemplate,
        recurringDeleteMessageTemplate = strings.recurringDeleteMessageTemplate,
        onDeleteItem = onDeleteItem,
        onDeleteSeries = onDeleteSeries,
        onDismiss = onDismiss
    )
}

internal fun List<IncomeSection>.toTransactionSections(
    categoriesById: Map<String, Category>
): List<GroupedTransactionSection<Income>> =
    map { section ->
        val firstCategoryId = section.incomes.firstOrNull()?.categoryId
        GroupedTransactionSection(
            key = section.key,
            title = section.title,
            totalAmount = section.totalAmount,
            categoryId = firstCategoryId,
            categoryIconKey = firstCategoryId?.let(categoriesById::get)?.icon,
            items = section.incomes
        )
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MonthlyIncomeRow(
    income: Income,
    categoryIconKey: String?,
    categoryColorKey: String?,
    onOpenIncome: (String) -> Unit,
    onDeleteIncome: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val currencySymbol = stringResource(Res.string.currency_symbol)
    val incomeLabel = stringResource(Res.string.income)
    val currentOnDeleteIncome by rememberUpdatedState(onDeleteIncome)
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { distance ->
            distance * 0.35f
        }
    )
    val handleDismiss = remember(income.id, dismissState, scope) {
        { dismissValue: SwipeToDismissBoxValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                currentOnDeleteIncome(income.id)
                scope.launch {
                    dismissState.reset()
                }
            }
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        onDismiss = handleDismiss,
        backgroundContent = {
            DeleteExpenseBackground()
        }
    ) {
        ExpenseListItemRow(
            title = income.description?.ifBlank { incomeLabel } ?: incomeLabel,
            subtitleText = formatExpenseDateGroupTitle(epochMillisToLocalDate(income.date)),
            amountText = formatAmount(income.amount, currencySymbol),
            categoryIconKey = categoryIconKey,
            categoryColorKey = categoryColorKey,
            isRecurring = !income.recurringSeriesId.isNullOrBlank(),
            subtitleFontSizeOffsetSp = -2,
            onClick = { onOpenIncome(income.id) }
        )
    }
}
