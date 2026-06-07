package it.danielebufarini.spesify.ui.screens.expenses

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import it.danielebufarini.spesify.data.CategoryManagementRepository
import it.danielebufarini.spesify.data.ExpenseReadRepository
import it.danielebufarini.spesify.data.PersistentWriteScope
import it.danielebufarini.spesify.data.TransactionWriteRepository
import it.danielebufarini.spesify.data.monthBounds
import it.danielebufarini.spesify.database.Expense
import it.danielebufarini.spesify.getPlatform
import it.danielebufarini.spesify.localization.rememberCategoryNameResolver
import it.danielebufarini.spesify.ui.screens.ExpenseGroupingMode
import it.danielebufarini.spesify.ui.screens.TransactionSearchPaging
import it.danielebufarini.spesify.ui.screens.TransactionSearchResults
import it.danielebufarini.spesify.ui.screens.buildGroupedExpensesState
import it.danielebufarini.spesify.ui.screens.categories.EnsureStarterCategoriesSeeded
import it.danielebufarini.spesify.ui.screens.collectAsFlowLoadState
import it.danielebufarini.spesify.ui.screens.common.MonthCursor
import it.danielebufarini.spesify.ui.screens.rememberGroupedTransactionRouteState
import it.danielebufarini.spesify.ui.screens.searchExpenseCandidatePages
import it.danielebufarini.spesify.ui.screens.transactionSearchPaging
import it.danielebufarini.spesify.ui.screens.transactions.AddTransactionScreen
import it.danielebufarini.spesify.ui.screens.transactions.TransactionEditorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringArrayResource
import org.koin.compose.koinInject
import spesify.composeapp.generated.resources.Res
import spesify.composeapp.generated.resources.full_month_names
import spesify.composeapp.generated.resources.short_month_names


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
        val routeState = rememberGroupedTransactionRouteState(
            initialMonth = MonthCursor(year, month),
            searchQuery = searchQuery,
        )
        var pendingExpenseDelete by remember { mutableStateOf<Expense?>(null) }
        val searchPaging = transactionSearchPaging(
            searchQuery = searchQuery,
            externalSearchPageCount = externalSearchPageCount,
            localSearchPageCount = routeState.localSearchPageCount,
            onLocalSearchPageCountChange = routeState::updateLocalSearchPageCount,
            onLoadMoreSearchResults = onLoadMoreSearchResults
        )

        EnsureStarterCategoriesSeeded(categoryRepository)

        val routeData = rememberGroupedExpensesRouteData(
            categoryRepository = categoryRepository,
            expenseReadRepository = expenseReadRepository,
            selectedMonth = routeState.selectedMonth,
            groupingMode = routeState.groupingMode,
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
            onGroupingModeChange = routeState::selectGroupingMode,
            onLoadMoreSearchResults = searchPaging.loadMoreSearchResults,
            onPreviousMonth = routeState::previousMonth,
            onNextMonth = routeState::nextMonth
        )
        val scaffoldState = GroupedExpensesScaffoldState(
            showNavigationChrome = showNavigationChrome,
            selectedMonth = routeState.selectedMonth,
            screenTitle = screenTitle(monthName(routeState.selectedMonth.month, fullMonthNames)),
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
            groupingMode = routeState.groupingMode,
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
                isGroupedByDate = routeState.groupingMode == ExpenseGroupingMode.ByDate,
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
