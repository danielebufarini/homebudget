package it.danielebufarini.homebudget.ui.screens.income

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.add_income
import homebudget.composeapp.generated.resources.back
import homebudget.composeapp.generated.resources.by_category
import homebudget.composeapp.generated.resources.by_date
import homebudget.composeapp.generated.resources.currency_symbol
import homebudget.composeapp.generated.resources.delete
import homebudget.composeapp.generated.resources.delete_item_confirmation_message
import homebudget.composeapp.generated.resources.delete_recurring_item_confirmation_message
import homebudget.composeapp.generated.resources.income
import homebudget.composeapp.generated.resources.load_more_search_results
import homebudget.composeapp.generated.resources.no_income_for_month
import homebudget.composeapp.generated.resources.short_month_names
import homebudget.composeapp.generated.resources.unknown_category
import it.danielebufarini.homebudget.data.DEFAULT_TRANSACTION_SEARCH_PAGE_SIZE
import it.danielebufarini.homebudget.data.ExpenseRepository
import it.danielebufarini.homebudget.data.formatAmount
import it.danielebufarini.homebudget.data.monthBounds
import it.danielebufarini.homebudget.database.Category
import it.danielebufarini.homebudget.database.Income
import it.danielebufarini.homebudget.localization.rememberCategoryNameResolver
import it.danielebufarini.homebudget.ui.screens.ExpenseGroupingMode
import it.danielebufarini.homebudget.ui.screens.GroupedTransactionListContent
import it.danielebufarini.homebudget.ui.screens.GroupedTransactionSection
import it.danielebufarini.homebudget.ui.screens.GroupedTransactionSectionStyle
import it.danielebufarini.homebudget.ui.screens.buildGroupedIncomesState
import it.danielebufarini.homebudget.ui.screens.collectAsFlowLoadState
import it.danielebufarini.homebudget.ui.screens.common.MonthCursor
import it.danielebufarini.homebudget.ui.screens.common.MonthNavigationTitle
import it.danielebufarini.homebudget.ui.screens.edgeToEdgeListContentPadding
import it.danielebufarini.homebudget.ui.screens.emptyGroupedIncomesState
import it.danielebufarini.homebudget.ui.screens.expenses.DeleteExpenseBackground
import it.danielebufarini.homebudget.ui.screens.expenses.ExpenseListItemRow
import it.danielebufarini.homebudget.ui.screens.expenses.epochMillisToLocalDate
import it.danielebufarini.homebudget.ui.screens.expenses.formatExpenseDateGroupTitle
import it.danielebufarini.homebudget.ui.screens.monthSwipeNavigation
import it.danielebufarini.homebudget.ui.screens.platform.rememberIsIosPlatform
import it.danielebufarini.homebudget.ui.screens.searchIncomeCandidatePages
import it.danielebufarini.homebudget.ui.screens.transactions.AddTransactionScreen
import it.danielebufarini.homebudget.ui.screens.transactions.BottomTransactionQuickActions
import it.danielebufarini.homebudget.ui.screens.transactions.TransactionDeleteConfirmationDialog
import it.danielebufarini.homebudget.ui.screens.transactions.TransactionEditorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private data class MonthlyIncomeStrings(
    val addIncome: String,
    val back: String,
    val byCategory: String,
    val byDate: String,
    val currencySymbol: String,
    val delete: String,
    val deleteItemConfirmationMessageTemplate: String,
    val recurringDeleteMessageTemplate: String,
    val income: String,
    val noIncomeForMonth: String,
    val loadMoreSearchResults: String
)

@Composable
private fun rememberMonthlyIncomeStrings(): MonthlyIncomeStrings =
    MonthlyIncomeStrings(
        addIncome = stringResource(Res.string.add_income),
        back = stringResource(Res.string.back),
        byCategory = stringResource(Res.string.by_category),
        byDate = stringResource(Res.string.by_date),
        currencySymbol = stringResource(Res.string.currency_symbol),
        delete = stringResource(Res.string.delete),
        deleteItemConfirmationMessageTemplate = stringResource(Res.string.delete_item_confirmation_message),
        recurringDeleteMessageTemplate = stringResource(Res.string.delete_recurring_item_confirmation_message),
        income = stringResource(Res.string.income),
        noIncomeForMonth = stringResource(Res.string.no_income_for_month),
        loadMoreSearchResults = stringResource(Res.string.load_more_search_results)
    )

class MonthlyIncomesScreen(
    private val year: Int,
    private val month: Int,
    private val searchQuery: String = "",
    private val externalSearchPageCount: Int? = null,
    private val onLoadMoreSearchResults: (() -> Unit)? = null
) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        RouteContent(
            initialMonth = MonthCursor(year, month),
            showNavigationChrome = true,
            onBack = { navigator?.pop() },
            onAddIncome = { selectedYear, selectedMonth ->
                navigator?.push(
                    AddTransactionScreen(
                        initialKind = TransactionEditorKind.Income,
                        initialIncomeYear = selectedYear,
                        initialIncomeMonth = selectedMonth
                    )
                )
            },
            onOpenIncome = { incomeId ->
                navigator?.push(AddIncomeScreen(incomeId))
            }
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun RouteContent(
        initialMonth: MonthCursor,
        showNavigationChrome: Boolean,
        onBack: () -> Unit,
        onAddIncome: (Int, Int) -> Unit,
        onOpenIncome: (String) -> Unit
    ) {
        val repository: ExpenseRepository = koinInject()
        val isIos = rememberIsIosPlatform()
        val scope = rememberCoroutineScope()
        val strings = rememberMonthlyIncomeStrings()
        val unknownCategoryLabel = stringResource(Res.string.unknown_category)
        val shortMonthNames = stringArrayResource(Res.array.short_month_names)
        val shortMonthNamesList = remember(shortMonthNames) { shortMonthNames.toList() }
        val resolveCategoryName = rememberCategoryNameResolver()
        var selectedMonth by remember(initialMonth) { mutableStateOf(initialMonth) }
        var groupingMode by remember { mutableStateOf(ExpenseGroupingMode.ByCategory) }
        var localSearchPageCount by remember(searchQuery) { mutableStateOf(1) }
        var pendingIncomeDelete by remember { mutableStateOf<Income?>(null) }
        val (monthStartMillis, monthEndMillis) = remember(selectedMonth) {
            monthBounds(selectedMonth.year, selectedMonth.month)
        }
        val searchMode = searchQuery.isNotBlank()
        val searchPageCount = if (searchMode) {
            externalSearchPageCount ?: localSearchPageCount
        } else {
            1
        }
        val loadedSearchCandidateCount = searchPageCount * DEFAULT_TRANSACTION_SEARCH_PAGE_SIZE
        val loadMoreSearchResults = onLoadMoreSearchResults ?: {
            localSearchPageCount += 1
        }
        val incomesFlow = remember(
            repository,
            monthStartMillis,
            monthEndMillis,
            searchMode,
            searchQuery,
            searchPageCount
        ) {
            if (searchMode) {
                repository.searchIncomeCandidatePages(
                    query = searchQuery,
                    pageCount = searchPageCount,
                    pageSize = DEFAULT_TRANSACTION_SEARCH_PAGE_SIZE
                )
            } else {
                repository.getIncomesBetween(monthStartMillis, monthEndMillis)
            }
        }
        val categoriesFlow = remember(repository) {
            repository.getAllCategories()
        }
        val groupedIncomesFlow = remember(
            incomesFlow,
            categoriesFlow,
            groupingMode,
            resolveCategoryName,
            unknownCategoryLabel,
            shortMonthNamesList,
            searchQuery,
            strings.currencySymbol
        ) {
            combine(incomesFlow, categoriesFlow) { incomes, categories ->
                val categoriesById = categories.associateBy { it.id }
                buildGroupedIncomesState(
                    incomes = incomes,
                    categoriesById = categoriesById,
                    groupingMode = groupingMode,
                    resolveCategoryName = { category: Category ->
                        resolveCategoryName(category.id, category.name)
                    },
                    unknownCategoryLabel = unknownCategoryLabel,
                    shortMonthNames = shortMonthNamesList,
                    searchQuery = searchQuery,
                    currencySymbol = strings.currencySymbol
                )
            }
                .distinctUntilChanged()
                .flowOn(Dispatchers.Default)
        }
        val groupedIncomesLoadKey = remember(monthStartMillis, monthEndMillis, searchMode, searchQuery) {
            "${monthStartMillis}:${monthEndMillis}:${searchMode}:${searchQuery}"
        }
        val groupedIncomesLoadState = groupedIncomesFlow.collectAsFlowLoadState(
            initialValue = emptyGroupedIncomesState(),
            resetKey = groupedIncomesLoadKey
        )
        val groupedIncomesState = groupedIncomesLoadState.value
        val groupedIncomes = groupedIncomesState.sections
        val totalAmount = groupedIncomesState.totalAmount
        val categoriesById = groupedIncomesState.categoriesById
        val canLoadMoreSearchResults = searchMode &&
            groupedIncomesState.candidateCount >= loadedSearchCandidateCount
        val deleteIncomeAction: (String) -> Unit = deleteAction@{ incomeId ->
            val income = groupedIncomesState.visibleIncomes.find { it.id == incomeId }
                ?: return@deleteAction
            pendingIncomeDelete = income
        }
        val content: @Composable (PaddingValues) -> Unit = { padding ->
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
                        onPreviousMonth = { selectedMonth = selectedMonth.previous() },
                        onNextMonth = { selectedMonth = selectedMonth.next() }
                    )
            ) {
                GroupedTransactionListContent(
                    sections = groupedIncomes.map { section ->
                        val firstCategoryId = section.incomes.firstOrNull()?.categoryId
                        GroupedTransactionSection(
                            key = section.key,
                            title = section.title,
                            totalAmount = section.totalAmount,
                            categoryId = firstCategoryId,
                            categoryIconKey = firstCategoryId?.let(categoriesById::get)?.icon,
                            items = section.incomes
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                    groupingMode = groupingMode,
                    onGroupingModeChange = { groupingMode = it },
                    emptyStateText = strings.noIncomeForMonth,
                    currencySymbol = strings.currencySymbol,
                    byCategoryLabel = strings.byCategory,
                    byDateLabel = strings.byDate,
                    groupsExpandedByDefault = false,
                    sectionStyle = GroupedTransactionSectionStyle(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        textStyle = MaterialTheme.typography.titleMedium,
                        iconTint = null,
                        chevronContainerColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.16f),
                        chevronContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    showGroupingControls = showFloatingBottomControls,
                    listContentPadding = listContentPadding,
                    bottomControlsBottomPadding = bottomControlsPadding,
                    loadMoreSearchResultsLabel = strings.loadMoreSearchResults,
                    canLoadMoreSearchResults = canLoadMoreSearchResults,
                    onLoadMoreSearchResults = loadMoreSearchResults,
                    isLoading = groupedIncomesLoadState.isLoading,
                    emptyStateCentered = true,
                    itemKey = Income::id
                ) { income ->
                    val category = income.categoryId?.let(categoriesById::get)
                    MonthlyIncomeRow(
                        income = income,
                        categoryIconKey = category?.icon,
                        categoryColorKey = income.categoryId,
                        onOpenIncome = onOpenIncome,
                        onDeleteIncome = deleteIncomeAction
                    )
                }
            }
        }

        if (showNavigationChrome) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
                contentWindowInsets = WindowInsets.systemBars,
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            MonthNavigationTitle(
                                selectedMonth = selectedMonth,
                                subtitle = "${strings.income} • ${formatAmount(totalAmount, strings.currencySymbol)}",
                                onPreviousMonth = { selectedMonth = selectedMonth.previous() },
                                onNextMonth = { selectedMonth = selectedMonth.next() }
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
                                    onAddTransaction = { onAddIncome(selectedMonth.year, selectedMonth.month) },
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                            }
                        }
                    )
                }
            ) { padding ->
                content(padding)
            }
        } else {
            content(PaddingValues(0.dp))
        }

        pendingIncomeDelete?.let { income ->
            val incomeDisplayName = income.description?.ifBlank { strings.income } ?: strings.income
            TransactionDeleteConfirmationDialog(
                itemDisplayName = incomeDisplayName,
                recurringSeriesId = income.recurringSeriesId,
                deleteTitle = strings.delete,
                deleteItemConfirmationMessageTemplate = strings.deleteItemConfirmationMessageTemplate,
                recurringDeleteMessageTemplate = strings.recurringDeleteMessageTemplate,
                onDeleteItem = {
                    pendingIncomeDelete = null
                    scope.launch {
                        repository.deleteIncome(income.id)
                    }
                },
                onDeleteSeries = { seriesId ->
                    pendingIncomeDelete = null
                    scope.launch {
                        repository.deleteRecurringIncomeSeries(seriesId)
                    }
                },
                onDismiss = {
                    pendingIncomeDelete = null
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthlyIncomeRow(
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
