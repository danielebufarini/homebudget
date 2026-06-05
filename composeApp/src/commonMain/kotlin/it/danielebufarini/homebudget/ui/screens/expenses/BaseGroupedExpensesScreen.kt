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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.add_expense
import homebudget.composeapp.generated.resources.back
import homebudget.composeapp.generated.resources.by_category
import homebudget.composeapp.generated.resources.by_date
import homebudget.composeapp.generated.resources.currency_symbol
import homebudget.composeapp.generated.resources.delete
import homebudget.composeapp.generated.resources.delete_item_confirmation_message
import homebudget.composeapp.generated.resources.delete_recurring_item_confirmation_message
import homebudget.composeapp.generated.resources.full_month_names
import homebudget.composeapp.generated.resources.load_more_search_results
import homebudget.composeapp.generated.resources.short_month_names
import homebudget.composeapp.generated.resources.unable_to_delete_expense
import homebudget.composeapp.generated.resources.unknown_category
import it.danielebufarini.homebudget.data.CategoryManagementRepository
import it.danielebufarini.homebudget.data.ExpenseReadRepository
import it.danielebufarini.homebudget.data.PersistentWriteScope
import it.danielebufarini.homebudget.data.TransactionWriteRepository
import it.danielebufarini.homebudget.data.monthBounds
import it.danielebufarini.homebudget.database.Category
import it.danielebufarini.homebudget.database.Expense
import it.danielebufarini.homebudget.getPlatform
import it.danielebufarini.homebudget.localization.rememberCategoryNameResolver
import it.danielebufarini.homebudget.ui.screens.ExpenseGroupingMode
import it.danielebufarini.homebudget.ui.screens.ExpenseSection
import it.danielebufarini.homebudget.ui.screens.TransactionSearchPaging
import it.danielebufarini.homebudget.ui.screens.TransactionSearchResults
import it.danielebufarini.homebudget.ui.screens.buildGroupedExpensesState
import it.danielebufarini.homebudget.ui.screens.categories.EnsureStarterCategoriesSeeded
import it.danielebufarini.homebudget.ui.screens.collectAsFlowLoadState
import it.danielebufarini.homebudget.ui.screens.common.MonthCursor
import it.danielebufarini.homebudget.ui.screens.edgeToEdgeListContentPadding
import it.danielebufarini.homebudget.ui.screens.monthSwipeNavigation
import it.danielebufarini.homebudget.ui.screens.searchExpenseCandidatePages
import it.danielebufarini.homebudget.ui.screens.transactionSearchPaging
import it.danielebufarini.homebudget.ui.screens.transactions.AddTransactionScreen
import it.danielebufarini.homebudget.ui.screens.transactions.TransactionDeleteConfirmationDialog
import it.danielebufarini.homebudget.ui.screens.transactions.TransactionEditorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private data class GroupedExpenseStrings(
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

private data class GroupedExpensesRouteData(
    val groupedExpenses: List<ExpenseSection>,
    val visibleExpenses: List<Expense>,
    val totalAmount: Long,
    val categoriesById: Map<String, Category>,
    val canLoadMoreSearchResults: Boolean,
    val isLoading: Boolean
)

private data class GroupedExpensesScaffoldState(
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

private data class GroupedExpensesContentState(
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

private data class GroupedExpensesRouteActions(
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
private fun rememberGroupedExpenseStrings(): GroupedExpenseStrings =
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

abstract class BaseGroupedExpensesScreen(
    private val year: Int,
    private val month: Int,
    private val searchQuery: String = "",
    private val externalSearchPageCount: Int? = null,
    private val onLoadMoreSearchResults: (() -> Unit)? = null
) : Screen {

    @Composable
    protected abstract fun screenTitle(monthName: String): String
    @Composable
    protected abstract fun emptyStateText(): String
    @Composable
    protected abstract fun expenseFallbackTitle(): String
    protected abstract fun includeExpense(expense: Expense): Boolean

    protected open fun centerAlignedTitle(): Boolean = false
    protected open fun groupsExpandedByDefault(): Boolean = false
    protected open fun includeCategory(categoryName: String): Boolean = true
    protected open fun canDeleteExpense(): Boolean = true
    protected open fun canAddExpense(): Boolean = false
    protected open fun showMonthNavigationControls(): Boolean = true

    @Composable
    protected open fun sectionHeaderContainerColor(): Color = Color.Transparent

    @Composable
    protected open fun sectionHeaderContentColor(): Color = MaterialTheme.colorScheme.onSurface

    @Composable
    protected open fun sectionHeaderTextStyle(): TextStyle = MaterialTheme.typography.bodyLarge

    @Composable
    protected open fun sectionHeaderIconTint(): Color? = null

    @Composable
    protected open fun sectionHeaderChevronContainerColor(): Color? = null

    @Composable
    protected open fun sectionHeaderChevronContentColor(): Color? = null

    @Composable
    protected open fun monthNavigationDescriptor(): String? = null

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        RouteContent(
            showNavigationChrome = true,
            onBack = { navigator?.pop() },
            onAddExpense = {
                navigator?.push(
                    AddTransactionScreen(
                        initialKind = TransactionEditorKind.Expense
                    )
                )
            },
            onOpenExpense = { expenseId ->
                navigator?.push(AddExpenseScreen(expenseId))
            }
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun RouteContent(
        showNavigationChrome: Boolean,
        onBack: () -> Unit,
        onAddExpense: () -> Unit,
        onOpenExpense: (String) -> Unit
    ) {
        val categoryRepository: CategoryManagementRepository = koinInject()
        val expenseReadRepository: ExpenseReadRepository = koinInject()
        val transactionWriteRepository: TransactionWriteRepository = koinInject()
        val writeScope: PersistentWriteScope = koinInject()
        val notificationScope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }
        val isIos = remember { getPlatform().isIos }
        val strings = rememberGroupedExpenseStrings()
        val fullMonthNames = stringArrayResource(Res.array.full_month_names)
        val shortMonthNames = stringArrayResource(Res.array.short_month_names)
        val shortMonthNamesList = remember(shortMonthNames) { shortMonthNames.toList() }
        val resolveCategoryName = rememberCategoryNameResolver()
        val emptyStateText = emptyStateText()
        val expenseFallbackTitle = expenseFallbackTitle()
        val navigationDescriptor = monthNavigationDescriptor()
        var selectedMonth by remember(year, month) { mutableStateOf(MonthCursor(year, month)) }
        var groupingMode by remember { mutableStateOf(ExpenseGroupingMode.ByCategory) }
        var localSearchPageCount by remember(searchQuery) { mutableStateOf(1) }
        var pendingExpenseDelete by remember { mutableStateOf<Expense?>(null) }
        val searchPaging = transactionSearchPaging(
            searchQuery = searchQuery,
            externalSearchPageCount = externalSearchPageCount,
            localSearchPageCount = localSearchPageCount,
            onLocalSearchPageCountChange = { localSearchPageCount = it },
            onLoadMoreSearchResults = onLoadMoreSearchResults
        )

        EnsureStarterCategoriesSeeded(categoryRepository)

        val routeData = rememberGroupedExpensesRouteData(
            categoryRepository = categoryRepository,
            expenseReadRepository = expenseReadRepository,
            selectedMonth = selectedMonth,
            groupingMode,
            searchPaging = searchPaging,
            shortMonthNames = shortMonthNamesList,
            strings = strings,
            resolveCategoryName = resolveCategoryName
        )
        val deleteExpenseAction: ((String) -> Unit)? = if (canDeleteExpense()) {
            deleteAction@{ expenseId ->
                val expense = routeData.visibleExpenses.find { it.id == expenseId }
                    ?: return@deleteAction
                pendingExpenseDelete = expense
            }
        } else {
            null
        }

        val routeActions = GroupedExpensesRouteActions(
            onBack = onBack,
            onAddExpense = onAddExpense,
            onOpenExpense = onOpenExpense,
            onDeleteExpense = deleteExpenseAction,
            onGroupingModeChange = { groupingMode = it },
            onLoadMoreSearchResults = searchPaging.loadMoreSearchResults,
            onPreviousMonth = { selectedMonth = selectedMonth.previous() },
            onNextMonth = { selectedMonth = selectedMonth.next() }
        )
        val scaffoldState = GroupedExpensesScaffoldState(
            showNavigationChrome = showNavigationChrome,
            selectedMonth = selectedMonth,
            screenTitle = screenTitle(monthName(selectedMonth.month, fullMonthNames)),
            navigationDescriptor = navigationDescriptor,
            totalAmount = routeData.totalAmount,
            isIos = isIos,
            canAddExpense = canAddExpense(),
            showMonthNavigationControls = showMonthNavigationControls(),
            strings = strings,
            snackbarHostState = snackbarHostState
        )
        val contentState = GroupedExpensesContentState(
            routeData = routeData,
            groupingMode = groupingMode,
            showFloatingBottomControls = showNavigationChrome && !isIos,
            monthSwipeEnabled = showNavigationChrome && showMonthNavigationControls(),
            emptyStateText = emptyStateText,
            expenseFallbackTitle = expenseFallbackTitle,
            strings = strings,
            groupsExpandedByDefault = groupsExpandedByDefault(),
            sectionStyle = groupedExpenseSectionStyle()
        )

        GroupedExpensesRouteScaffold(
            state = scaffoldState,
            actions = routeActions
        ) { padding ->
            GroupedExpensesRouteContent(
                padding = padding,
                state = contentState,
                actions = routeActions,
                resolveCategoryName = { category ->
                    resolveCategoryName(category.id, category.name)
                }
            )
        }

        pendingExpenseDelete?.let { expense ->
            val showDeleteFailure = {
                notificationScope.launch {
                    snackbarHostState.showSnackbar(strings.unableToDeleteExpense)
                }
            }

            ExpenseDeleteDialog(
                expense = expense,
                categoriesById = routeData.categoriesById,
                isGroupedByDate = groupingMode == ExpenseGroupingMode.ByDate,
                expenseFallbackTitle = expenseFallbackTitle,
                strings = strings,
                resolveCategoryName = { category ->
                    resolveCategoryName(category.id, category.name)
                },
                onDeleteItem = {
                    pendingExpenseDelete = null
                    writeScope.launchWrite(
                        onFailure = { showDeleteFailure() }
                    ) {
                        transactionWriteRepository.deleteExpense(expense.id)
                    }
                },
                onDeleteSeries = { seriesId ->
                    pendingExpenseDelete = null
                    writeScope.launchWrite(
                        onFailure = { showDeleteFailure() }
                    ) {
                        transactionWriteRepository.deleteRecurringExpenseSeries(seriesId)
                    }
                },
                onDismiss = {
                    pendingExpenseDelete = null
                }
            )
        }
    }

    @Composable
    private fun rememberGroupedExpensesRouteData(
        categoryRepository: CategoryManagementRepository,
        expenseReadRepository: ExpenseReadRepository,
        selectedMonth: MonthCursor,
        groupingMode: ExpenseGroupingMode,
        searchPaging: TransactionSearchPaging,
        shortMonthNames: List<String>,
        strings: GroupedExpenseStrings,
        resolveCategoryName: (String, String) -> String
    ): GroupedExpensesRouteData {
        val (monthStartMillis, monthEndMillis) = remember(selectedMonth) {
            monthBounds(selectedMonth.year, selectedMonth.month)
        }
        val expensesFlow = remember(
            expenseReadRepository,
            monthStartMillis,
            monthEndMillis,
            searchPaging.searchMode,
            searchQuery,
            searchPaging.pageCount
        ) {
            if (searchPaging.searchMode) {
                expenseReadRepository.searchExpenseCandidatePages(
                    query = searchQuery,
                    pageCount = searchPaging.pageCount
                )
            } else {
                expenseReadRepository.getExpensesBetween(monthStartMillis, monthEndMillis)
                    .map { expenses ->
                        TransactionSearchResults(
                            items = expenses,
                            canLoadMore = false
                        )
                    }
            }
        }
        val categoriesFlow = remember(categoryRepository) {
            categoryRepository.getAllCategories()
        }
        val groupedExpensesFlow = remember(
            expensesFlow,
            categoriesFlow,
            groupingMode,
            resolveCategoryName,
            strings.unknownCategory,
            shortMonthNames,
            searchQuery,
            strings.currencySymbol
        ) {
            combine(expensesFlow, categoriesFlow) { expenseResults, categories ->
                val categoriesById = categories.associateBy { it.id }
                val groupedState = buildGroupedExpensesState(
                    expenses = expenseResults.items,
                    categoriesById = categoriesById,
                    groupingMode = groupingMode,
                    includeExpense = ::includeExpense,
                    includeCategory = ::includeCategory,
                    resolveCategoryName = { category ->
                        resolveCategoryName(category.id, category.name)
                    },
                    unknownCategoryLabel = strings.unknownCategory,
                    shortMonthNames = shortMonthNames,
                    searchQuery = searchQuery,
                    currencySymbol = strings.currencySymbol
                )
                GroupedExpensesRouteData(
                    groupedExpenses = groupedState.sections,
                    visibleExpenses = groupedState.visibleExpenses,
                    totalAmount = groupedState.totalAmount,
                    categoriesById = groupedState.categoriesById,
                    canLoadMoreSearchResults = searchPaging.searchMode && expenseResults.canLoadMore,
                    isLoading = false
                )
            }
                .distinctUntilChanged()
                .flowOn(Dispatchers.Default)
        }
        val loadKey = remember(monthStartMillis, monthEndMillis, searchPaging.searchMode, searchQuery) {
            "${monthStartMillis}:${monthEndMillis}:${searchPaging.searchMode}:${searchQuery}"
        }
        val loadState = groupedExpensesFlow.collectAsFlowLoadState(
            initialValue = GroupedExpensesRouteData(
                groupedExpenses = emptyList(),
                visibleExpenses = emptyList(),
                totalAmount = 0L,
                categoriesById = emptyMap(),
                canLoadMoreSearchResults = false,
                isLoading = true
            ),
            resetKey = loadKey
        )
        return loadState.value.copy(isLoading = loadState.isLoading)
    }

    @Composable
    private fun groupedExpenseSectionStyle(): GroupedExpenseSectionStyle {
        return GroupedExpenseSectionStyle(
            containerColor = sectionHeaderContainerColor(),
            contentColor = sectionHeaderContentColor(),
            textStyle = sectionHeaderTextStyle(),
            iconTint = sectionHeaderIconTint(),
            chevronContainerColor = sectionHeaderChevronContainerColor(),
            chevronContentColor = sectionHeaderChevronContentColor()
        )
    }

    protected fun monthName(month: Int, fullMonthNames: List<String>): String {
        return fullMonthNames[month - 1]
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupedExpensesRouteScaffold(
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
private fun GroupedExpensesRouteContent(
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
private fun ExpenseDeleteDialog(
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
