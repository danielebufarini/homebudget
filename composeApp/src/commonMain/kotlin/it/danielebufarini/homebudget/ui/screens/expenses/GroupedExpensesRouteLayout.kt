package it.danielebufarini.homebudget.ui.screens.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.danielebufarini.homebudget.database.Category
import it.danielebufarini.homebudget.database.Expense
import it.danielebufarini.homebudget.ui.screens.edgeToEdgeListContentPadding
import it.danielebufarini.homebudget.ui.screens.monthSwipeNavigation
import it.danielebufarini.homebudget.ui.screens.transactions.TransactionDeleteConfirmationDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GroupedExpensesRouteScaffold(
    state: GroupedExpensesScaffoldState,
    actions: GroupedExpensesRouteActions,
    content: @Composable (PaddingValues) -> Unit
) {
    if (!state.showNavigationChrome) {
        Box(modifier = Modifier.fillMaxSize()) {
            content(PaddingValues(0.dp))
            SnackbarHost(
                hostState = state.snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        contentWindowInsets = WindowInsets.systemBars,
        snackbarHost = {
            SnackbarHost(hostState = state.snackbarHostState)
        },
        topBar = {
            GroupedExpensesTopBar(
                selectedMonth = state.selectedMonth,
                title = state.screenTitle,
                navigationDescriptor = state.navigationDescriptor,
                totalAmount = state.totalAmount,
                currencySymbol = state.strings.currencySymbol,
                isIos = state.isIos,
                canAddExpense = state.canAddExpense,
                showMonthNavigationControls = state.showMonthNavigationControls,
                backLabel = state.strings.back,
                addExpenseLabel = state.strings.addExpense,
                onBack = actions.onBack,
                onAddExpense = actions.onAddExpense,
                onPreviousMonth = actions.onPreviousMonth,
                onNextMonth = actions.onNextMonth
            )
        },
        content = content
    )
}

@Composable
internal fun GroupedExpensesRouteContent(
    padding: PaddingValues,
    state: GroupedExpensesContentState,
    actions: GroupedExpensesRouteActions,
    resolveCategoryName: (Category) -> String,
) {
    val bottomControlClearance = if (state.showFloatingBottomControls) 88.dp else 0.dp
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
                enabled = state.monthSwipeEnabled,
                onPreviousMonth = actions.onPreviousMonth,
                onNextMonth = actions.onNextMonth
            )
    ) {
        GroupedExpensesContent(
            groupedExpenses = state.routeData.groupedExpenses,
            categoriesById = state.routeData.categoriesById,
            modifier = Modifier.fillMaxSize(),
            groupingMode = state.groupingMode,
            onGroupingModeChange = actions.onGroupingModeChange,
            onOpenExpense = actions.onOpenExpense,
            onDeleteExpense = actions.onDeleteExpense,
            emptyStateText = state.emptyStateText,
            expenseFallbackTitle = state.expenseFallbackTitle,
            currencySymbol = state.strings.currencySymbol,
            unknownCategoryLabel = state.strings.unknownCategory,
            resolveCategoryName = resolveCategoryName,
            byCategoryLabel = state.strings.byCategory,
            byDateLabel = state.strings.byDate,
            groupsExpandedByDefault = state.groupsExpandedByDefault,
            sectionStyle = state.sectionStyle,
            showGroupingControls = !state.showFloatingBottomControls,
            listContentPadding = listContentPadding,
            bottomControlsBottomPadding = bottomControlsPadding,
            loadMoreSearchResultsLabel = state.strings.loadMoreSearchResults,
            canLoadMoreSearchResults = state.routeData.canLoadMoreSearchResults,
            onLoadMoreSearchResults = actions.onLoadMoreSearchResults,
            isLoading = state.routeData.isLoading
        )

        if (state.showFloatingBottomControls) {
            GroupingModeButtons(
                groupingMode = state.groupingMode,
                onGroupingModeChange = actions.onGroupingModeChange,
                byCategoryLabel = state.strings.byCategory,
                byDateLabel = state.strings.byDate,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomControlsPadding)
            )
        }
    }
}

@Composable
internal fun ExpenseDeleteDialog(
    expense: Expense,
    categoriesById: Map<String, Category>,
    isGroupedByDate: Boolean,
    expenseFallbackTitle: String,
    strings: GroupedExpenseStrings,
    resolveCategoryName: (Category) -> String,
    onDeleteItem: () -> Unit,
    onDeleteSeries: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val expenseDisplayName = groupedExpenseRowPresentation(
        expense = expense,
        categoriesById = categoriesById,
        isGroupedByDate = isGroupedByDate,
        expenseFallbackTitle = expenseFallbackTitle,
        unknownCategoryLabel = strings.unknownCategory,
        resolveCategoryName = resolveCategoryName
    ).title

    TransactionDeleteConfirmationDialog(
        itemDisplayName = expenseDisplayName,
        recurringSeriesId = expense.recurringSeriesId,
        deleteTitle = strings.delete,
        deleteItemConfirmationMessageTemplate = strings.deleteItemConfirmationMessageTemplate,
        recurringDeleteMessageTemplate = strings.recurringDeleteMessageTemplate,
        onDeleteItem = onDeleteItem,
        onDeleteSeries = onDeleteSeries,
        onDismiss = onDismiss
    )
}
