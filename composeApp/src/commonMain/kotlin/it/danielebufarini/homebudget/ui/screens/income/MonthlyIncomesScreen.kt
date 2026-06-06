package it.danielebufarini.homebudget.ui.screens.income

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
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
import homebudget.composeapp.generated.resources.unable_to_delete_income
import homebudget.composeapp.generated.resources.unknown_category
import it.danielebufarini.homebudget.data.CategoryManagementRepository
import it.danielebufarini.homebudget.data.IncomeReadRepository
import it.danielebufarini.homebudget.data.PersistentWriteScope
import it.danielebufarini.homebudget.data.TransactionWriteRepository
import it.danielebufarini.homebudget.data.monthBounds
import it.danielebufarini.homebudget.database.Category
import it.danielebufarini.homebudget.database.Income
import it.danielebufarini.homebudget.localization.rememberCategoryNameResolver
import it.danielebufarini.homebudget.ui.screens.TransactionSearchResults
import it.danielebufarini.homebudget.ui.screens.buildGroupedIncomesState
import it.danielebufarini.homebudget.ui.screens.collectAsFlowLoadState
import it.danielebufarini.homebudget.ui.screens.common.MonthCursor
import it.danielebufarini.homebudget.ui.screens.emptyGroupedIncomesState
import it.danielebufarini.homebudget.ui.screens.platform.rememberIsIosPlatform
import it.danielebufarini.homebudget.ui.screens.rememberGroupedTransactionRouteState
import it.danielebufarini.homebudget.ui.screens.searchIncomeCandidatePages
import it.danielebufarini.homebudget.ui.screens.transactionSearchPaging
import it.danielebufarini.homebudget.ui.screens.transactions.AddTransactionScreen
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

internal data class MonthlyIncomeStrings(
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
    val loadMoreSearchResults: String,
    val unableToDeleteIncome: String
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
        loadMoreSearchResults = stringResource(Res.string.load_more_search_results),
        unableToDeleteIncome = stringResource(Res.string.unable_to_delete_income)
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
        val categoryRepository: CategoryManagementRepository = koinInject()
        val incomeReadRepository: IncomeReadRepository = koinInject()
        val transactionWriteRepository: TransactionWriteRepository = koinInject()
        val writeScope: PersistentWriteScope = koinInject()
        val notificationScope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }
        val isIos = rememberIsIosPlatform()
        val strings = rememberMonthlyIncomeStrings()
        val unknownCategoryLabel = stringResource(Res.string.unknown_category)
        val shortMonthNames = stringArrayResource(Res.array.short_month_names)
        val shortMonthNamesList = remember(shortMonthNames) { shortMonthNames.toList() }
        val resolveCategoryName = rememberCategoryNameResolver()
        val routeState = rememberGroupedTransactionRouteState(
            initialMonth = initialMonth,
            searchQuery = searchQuery,
        )
        var pendingIncomeDelete by remember { mutableStateOf<Income?>(null) }
        val (monthStartMillis, monthEndMillis) = remember(routeState.selectedMonth) {
            monthBounds(routeState.selectedMonth.year, routeState.selectedMonth.month)
        }
        val searchPaging = transactionSearchPaging(
            searchQuery = searchQuery,
            externalSearchPageCount = externalSearchPageCount,
            localSearchPageCount = routeState.localSearchPageCount,
            onLocalSearchPageCountChange = routeState::updateLocalSearchPageCount,
            onLoadMoreSearchResults = onLoadMoreSearchResults
        )
        val incomesFlow = remember(
            incomeReadRepository,
            monthStartMillis,
            monthEndMillis,
            searchPaging.searchMode,
            searchQuery,
            searchPaging.pageCount
        ) {
            if (searchPaging.searchMode) {
                incomeReadRepository.searchIncomeCandidatePages(
                    query = searchQuery,
                    pageCount = searchPaging.pageCount
                )
            } else {
                incomeReadRepository.getIncomesBetween(monthStartMillis, monthEndMillis)
                    .map { incomes ->
                        TransactionSearchResults(
                            items = incomes,
                            canLoadMore = false
                        )
                    }
            }
        }
        val categoriesFlow = remember(categoryRepository) {
            categoryRepository.getAllCategories()
        }
        val groupedIncomesFlow = remember(
            incomesFlow,
            categoriesFlow,
            routeState.groupingMode,
            resolveCategoryName,
            unknownCategoryLabel,
            shortMonthNamesList,
            searchQuery,
            strings.currencySymbol
        ) {
            combine(incomesFlow, categoriesFlow) { incomeResults, categories ->
                val categoriesById = categories.associateBy { it.id }
                val groupedState = buildGroupedIncomesState(
                    incomes = incomeResults.items,
                    categoriesById = categoriesById,
                    groupingMode = routeState.groupingMode,
                    resolveCategoryName = { category: Category ->
                        resolveCategoryName(category.id, category.name)
                    },
                    unknownCategoryLabel = unknownCategoryLabel,
                    shortMonthNames = shortMonthNamesList,
                    searchQuery = searchQuery,
                    currencySymbol = strings.currencySymbol
                )
                groupedState to incomeResults.canLoadMore
            }
                .distinctUntilChanged()
                .flowOn(Dispatchers.Default)
        }
        val groupedIncomesLoadKey = remember(monthStartMillis, monthEndMillis, searchPaging.searchMode, searchQuery) {
            "${monthStartMillis}:${monthEndMillis}:${searchPaging.searchMode}:${searchQuery}"
        }
        val groupedIncomesLoadState = groupedIncomesFlow.collectAsFlowLoadState(
            initialValue = emptyGroupedIncomesState() to false,
            resetKey = groupedIncomesLoadKey
        )
        val (groupedIncomesState, canLoadMoreIncomeResults) = groupedIncomesLoadState.value
        val groupedIncomes = groupedIncomesState.sections
        val totalAmount = groupedIncomesState.totalAmount
        val categoriesById = groupedIncomesState.categoriesById
        val canLoadMoreSearchResults = searchPaging.searchMode && canLoadMoreIncomeResults
        val incomeSections = remember(groupedIncomes, categoriesById) {
            groupedIncomes.toTransactionSections(categoriesById)
        }
        val deleteIncomeAction: (String) -> Unit = deleteAction@{ incomeId ->
            val income = groupedIncomesState.visibleIncomes.find { it.id == incomeId }
                ?: return@deleteAction
            pendingIncomeDelete = income
        }

        if (showNavigationChrome) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
                contentWindowInsets = WindowInsets.systemBars,
                snackbarHost = {
                    SnackbarHost(hostState = snackbarHostState)
                },
                topBar = {
                    MonthlyIncomeTopBar(
                        selectedMonth = routeState.selectedMonth,
                        totalAmount = totalAmount,
                        strings = strings,
                        isIos = isIos,
                        onBack = onBack,
                        onAddIncome = {
                            onAddIncome(
                                routeState.selectedMonth.year,
                                routeState.selectedMonth.month,
                            )
                        },
                        onPreviousMonth = routeState::previousMonth,
                        onNextMonth = routeState::nextMonth
                    )
                }
            ) { padding ->
                MonthlyIncomeContent(
                    padding = padding,
                    sections = incomeSections,
                    categoriesById = categoriesById,
                    groupingMode = routeState.groupingMode,
                    onGroupingModeChange = routeState::selectGroupingMode,
                    showNavigationChrome = showNavigationChrome,
                    isIos = isIos,
                    strings = strings,
                    canLoadMoreSearchResults = canLoadMoreSearchResults,
                    onLoadMoreSearchResults = searchPaging.loadMoreSearchResults,
                    isLoading = groupedIncomesLoadState.isLoading,
                    onOpenIncome = onOpenIncome,
                    onDeleteIncome = deleteIncomeAction,
                    onPreviousMonth = routeState::previousMonth,
                    onNextMonth = routeState::nextMonth
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                MonthlyIncomeContent(
                    padding = PaddingValues(0.dp),
                    sections = incomeSections,
                    categoriesById = categoriesById,
                    groupingMode = routeState.groupingMode,
                    onGroupingModeChange = routeState::selectGroupingMode,
                    showNavigationChrome = showNavigationChrome,
                    isIos = isIos,
                    strings = strings,
                    canLoadMoreSearchResults = canLoadMoreSearchResults,
                    onLoadMoreSearchResults = searchPaging.loadMoreSearchResults,
                    isLoading = groupedIncomesLoadState.isLoading,
                    onOpenIncome = onOpenIncome,
                    onDeleteIncome = deleteIncomeAction,
                    onPreviousMonth = routeState::previousMonth,
                    onNextMonth = routeState::nextMonth
                )
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }

        pendingIncomeDelete?.let { income ->
            val dismiss = { pendingIncomeDelete = null }
            val showDeleteFailure = {
                notificationScope.launch {
                    snackbarHostState.showSnackbar(strings.unableToDeleteIncome)
                }
            }

            IncomeDeleteDialog(
                income = income,
                strings = strings,
                onDeleteItem = {
                    dismiss()
                    writeScope.launchWrite(
                        onFailure = { showDeleteFailure() }
                    ) {
                        transactionWriteRepository.deleteIncome(income.id)
                    }
                },
                onDeleteSeries = { seriesId ->
                    dismiss()
                    writeScope.launchWrite(
                        onFailure = { showDeleteFailure() }
                    ) {
                        transactionWriteRepository.deleteRecurringIncomeSeries(seriesId)
                    }
                },
                onDismiss = dismiss
            )
        }
    }
}
