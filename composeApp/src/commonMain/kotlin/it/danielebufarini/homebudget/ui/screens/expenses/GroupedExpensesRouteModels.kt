package it.danielebufarini.homebudget.ui.screens.expenses

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.add_expense
import homebudget.composeapp.generated.resources.back
import homebudget.composeapp.generated.resources.by_category
import homebudget.composeapp.generated.resources.by_date
import homebudget.composeapp.generated.resources.currency_symbol
import homebudget.composeapp.generated.resources.delete
import homebudget.composeapp.generated.resources.delete_item_confirmation_message
import homebudget.composeapp.generated.resources.delete_recurring_item_confirmation_message
import homebudget.composeapp.generated.resources.load_more_search_results
import homebudget.composeapp.generated.resources.unable_to_delete_expense
import homebudget.composeapp.generated.resources.unknown_category
import it.danielebufarini.homebudget.database.Category
import it.danielebufarini.homebudget.database.Expense
import it.danielebufarini.homebudget.ui.screens.ExpenseGroupingMode
import it.danielebufarini.homebudget.ui.screens.ExpenseSection
import it.danielebufarini.homebudget.ui.screens.common.MonthCursor
import org.jetbrains.compose.resources.stringResource

internal data class GroupedExpenseStrings(
    val addExpense: String,
    val back: String,
    val byCategory: String,
    val byDate: String,
    val currencySymbol: String,
    val delete: String,
    val deleteItemConfirmationMessageTemplate: String,
    val recurringDeleteMessageTemplate: String,
    val unknownCategory: String,
    val loadMoreSearchResults: String,
    val unableToDeleteExpense: String
)

internal data class GroupedExpensesRouteData(
    val groupedExpenses: List<ExpenseSection>,
    val visibleExpenses: List<Expense>,
    val totalAmount: Long,
    val categoriesById: Map<String, Category>,
    val canLoadMoreSearchResults: Boolean,
    val isLoading: Boolean
)

internal data class GroupedExpensesScaffoldState(
    val showNavigationChrome: Boolean,
    val selectedMonth: MonthCursor,
    val screenTitle: String,
    val navigationDescriptor: String?,
    val totalAmount: Long,
    val isIos: Boolean,
    val canAddExpense: Boolean,
    val showMonthNavigationControls: Boolean,
    val strings: GroupedExpenseStrings,
    val snackbarHostState: SnackbarHostState
)

internal data class GroupedExpensesContentState(
    val routeData: GroupedExpensesRouteData,
    val groupingMode: ExpenseGroupingMode,
    val showFloatingBottomControls: Boolean,
    val monthSwipeEnabled: Boolean,
    val emptyStateText: String,
    val expenseFallbackTitle: String,
    val strings: GroupedExpenseStrings,
    val groupsExpandedByDefault: Boolean,
    val sectionStyle: GroupedExpenseSectionStyle
)

internal data class GroupedExpensesRouteActions(
    val onBack: () -> Unit,
    val onAddExpense: () -> Unit,
    val onOpenExpense: (String) -> Unit,
    val onDeleteExpense: ((String) -> Unit)?,
    val onGroupingModeChange: (ExpenseGroupingMode) -> Unit,
    val onLoadMoreSearchResults: () -> Unit,
    val onPreviousMonth: () -> Unit,
    val onNextMonth: () -> Unit
)

@Composable
internal fun rememberGroupedExpenseStrings(): GroupedExpenseStrings =
    GroupedExpenseStrings(
        addExpense = stringResource(Res.string.add_expense),
        back = stringResource(Res.string.back),
        byCategory = stringResource(Res.string.by_category),
        byDate = stringResource(Res.string.by_date),
        currencySymbol = stringResource(Res.string.currency_symbol),
        delete = stringResource(Res.string.delete),
        deleteItemConfirmationMessageTemplate = stringResource(Res.string.delete_item_confirmation_message),
        recurringDeleteMessageTemplate = stringResource(Res.string.delete_recurring_item_confirmation_message),
        unknownCategory = stringResource(Res.string.unknown_category),
        loadMoreSearchResults = stringResource(Res.string.load_more_search_results),
        unableToDeleteExpense = stringResource(Res.string.unable_to_delete_expense)
    )
