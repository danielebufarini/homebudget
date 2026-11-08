package it.danielebufarini.spesify.ui.screens.dashboard

import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import it.danielebufarini.spesify.data.CategoryManagementRepository
import it.danielebufarini.spesify.data.DashboardPreferencesStore
import it.danielebufarini.spesify.data.DashboardReadRepository
import it.danielebufarini.spesify.data.DashboardRecentTransactionType
import it.danielebufarini.spesify.data.subtractAmountsExact
import it.danielebufarini.spesify.ui.screens.categories.EnsureStarterCategoriesSeeded
import it.danielebufarini.spesify.ui.screens.common.MonthCursor
import it.danielebufarini.spesify.ui.screens.platform.rememberIsIosPlatform
import org.koin.compose.koinInject

@Composable
fun DashboardRoute(
    showNavigationChrome: Boolean,
    openVoiceExpenseRequest: Int = 0,
    showFab: Boolean,
    showQuickActions: Boolean = true,
    reserveQuickActionsSpace: Boolean = showQuickActions,
    showTransactionSearch: Boolean = true,
    onOpenCategories: () -> Unit,
    onOpenAddExpense: () -> Unit,
    onOpenVoiceExpense: () -> Unit = {},
    onOpenCsvTransfer: (() -> Unit)? = null,
    onNavigationDrawerVisibilityChange: (Boolean) -> Unit = {},
    onOpenDayExpenses: (Int, Int, Int) -> Unit,
    onOpenMonthlyIncomes: (Int, Int) -> Unit,
    onOpenMonthlyExpenses: (Int, Int) -> Unit,
    onOpenSharedExpenses: (Int, Int) -> Unit,
    onOpenRecurringExpenses: (Int, Int) -> Unit,
    onBalanceChartExpansionChange: (Boolean) -> Unit = {},
    onOpenCategoryTransactions: (Int, Int, String?, String) -> Unit = { _, _, _, _ -> },
    onOpenExpenseDetails: (String) -> Unit = {},
    onOpenIncomeDetails: (String) -> Unit = {},
    onOpenTransactionSearch: (Int, Int, String) -> Unit = { _, _, _ -> }
) {
    val dashboardRepository: DashboardReadRepository = koinInject()
    val categoryRepository: CategoryManagementRepository = koinInject()
    val dashboardPreferencesStore: DashboardPreferencesStore = koinInject()
    val strings = rememberDashboardStrings()
    val pinnedDashboardCard by dashboardPreferencesStore.pinnedDashboardCard.collectAsState()
    val hideFutureExpenses by dashboardPreferencesStore.hideFutureExpenses.collectAsState()

    val categoriesFlow = remember(categoryRepository) {
        categoryRepository.getAllCategories()
    }
    val categories by categoriesFlow.collectAsState(initial = emptyList())
    val categoriesById = remember(categories) {
        categories.associateBy { it.id }
    }

    val currentMonth = remember {
        currentMonthCursor()
    }
    var savedYear by rememberSaveable { mutableIntStateOf(currentMonth.year) }
    var savedMonth by rememberSaveable { mutableIntStateOf(currentMonth.month) }
    val initialMonth = remember { MonthCursor(savedYear, savedMonth) }
    val dashboardScope = rememberCoroutineScope()
    val dashboardStore = remember(dashboardRepository, dashboardPreferencesStore, dashboardScope, initialMonth) {
        DashboardStateStore(
            repository = dashboardRepository,
            initialMonth = initialMonth,
            scope = dashboardScope,
            hideFutureExpenses = dashboardPreferencesStore.hideFutureExpenses
        )
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(dashboardStore, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            dashboardStore.refreshRecentTransactionsWhileVisible()
        }
    }

    val selectedMonth by dashboardStore.selectedMonth.collectAsState()
    SideEffect {
        savedYear = selectedMonth.year
        savedMonth = selectedMonth.month
    }
    var searchQuery by rememberSaveable {
        mutableStateOf("")
    }
    var balanceChartExpanded by rememberSaveable {
        mutableStateOf(false)
    }
    var categoryBreakdownExpanded by rememberSaveable {
        mutableStateOf(false)
    }
    var recentTransactionsExpanded by rememberSaveable {
        mutableStateOf(false)
    }
    var recentTransactionsFilter by rememberSaveable {
        mutableStateOf(DashboardRecentTransactionFilter.All)
    }
    val collapseRecentTransactions = {
        recentTransactionsExpanded = false
        recentTransactionsFilter = DashboardRecentTransactionFilter.All
    }
    val submitSearch = {
        val trimmedQuery = searchQuery.trim()
        if (trimmedQuery.isNotEmpty()) {
            onOpenTransactionSearch(selectedMonth.year, selectedMonth.month, trimmedQuery)
        }
    }

    EnsureStarterCategoriesSeeded(categoryRepository)

    val summary by dashboardStore.summary.collectAsState()
    val chartState by dashboardStore.chartState.collectAsState()
    val recentTransactions by dashboardStore.recentTransactions.collectAsState()
    val recurringExpenseOverview by dashboardStore.recurringExpenseOverview.collectAsState()

    val monthlySavingsAmount = remember(summary.incomeAmount, summary.totalAmount) {
        subtractAmountsExact(summary.incomeAmount, summary.totalAmount)
    }
    val bodyMode = when {
        balanceChartExpanded -> DashboardBodyMode.BalanceChartExpanded
        categoryBreakdownExpanded -> DashboardBodyMode.CategoryBreakdownExpanded
        recentTransactionsExpanded -> DashboardBodyMode.RecentTransactionsExpanded
        else -> DashboardBodyMode.Standard
    }
    val bodyTransition = updateTransition(bodyMode, label = "dashboardExpansion")
    LockDashboardOrientation(landscape = balanceChartExpanded)
    LaunchedEffect(balanceChartExpanded) {
        onBalanceChartExpansionChange(balanceChartExpanded)
    }
    DisposableEffect(Unit) {
        onDispose {
            onBalanceChartExpansionChange(false)
        }
    }

    val dashboardBody: @Composable (Modifier) -> Unit = { modifier ->
        DashboardBody(
            modifier = modifier,
            strings = strings,
            showMonthHeaderCard = !showNavigationChrome,
            selectedMonth = selectedMonth,
            summary = summary,
            monthlySavingsAmount = monthlySavingsAmount,
            recurringTotalAmount = recurringExpenseOverview.totalAmount,
            chartState = chartState,
            recentTransactions = recentTransactions,
            transition = bodyTransition,
            recentTransactionsFilter = recentTransactionsFilter,
            hideFutureExpenses = hideFutureExpenses,
            onHideFutureExpensesChange = dashboardPreferencesStore::setHideFutureExpenses,
            pinnedDashboardCard = pinnedDashboardCard,
            onPinDashboardCard = dashboardPreferencesStore::pinDashboardCard,
            onExpandBalanceChart = {
                categoryBreakdownExpanded = false
                recentTransactionsExpanded = false
                balanceChartExpanded = true
            },
            onCollapseBalanceChart = {
                balanceChartExpanded = false
            },
            onExpandCategoryBreakdown = {
                balanceChartExpanded = false
                recentTransactionsExpanded = false
                categoryBreakdownExpanded = true
            },
            onCollapseCategoryBreakdown = { categoryBreakdownExpanded = false },
            onExpandRecentTransactions = {
                balanceChartExpanded = false
                categoryBreakdownExpanded = false
                recentTransactionsExpanded = true
            },
            onCollapseRecentTransactions = collapseRecentTransactions,
            onRecentTransactionsFilterChange = { recentTransactionsFilter = it },
            categoriesById = categoriesById,
            showTransactionSearch = showTransactionSearch,
            searchQuery = searchQuery,
            onPreviousMonth = dashboardStore::selectPreviousMonth,
            onNextMonth = dashboardStore::selectNextMonth,
            onSearchQueryChange = { searchQuery = it },
            onSearchSubmit = submitSearch,
            onOpenMonthlyIncomes = {
                onOpenMonthlyIncomes(selectedMonth.year, selectedMonth.month)
            },
            onOpenMonthlyExpenses = {
                onOpenMonthlyExpenses(selectedMonth.year, selectedMonth.month)
            },
            onOpenDayExpenses = { day ->
                onOpenDayExpenses(selectedMonth.year, selectedMonth.month, day)
            },
            onOpenSharedExpenses = {
                onOpenSharedExpenses(selectedMonth.year, selectedMonth.month)
            },
            onOpenRecurringExpenses = {
                onOpenRecurringExpenses(selectedMonth.year, selectedMonth.month)
            },
            onOpenCategoryTransactions = { categoryId, categoryName ->
                onOpenCategoryTransactions(
                    selectedMonth.year,
                    selectedMonth.month,
                    categoryId,
                    categoryName
                )
            },
            onOpenRecentTransaction = { transaction ->
                when (transaction.type) {
                    DashboardRecentTransactionType.Expense -> onOpenExpenseDetails(transaction.id)
                    DashboardRecentTransactionType.Income -> onOpenIncomeDetails(transaction.id)
                }
            }
        )
    }

    if (showNavigationChrome) {
        DashboardScreenScaffold(
            strings = strings,
            openVoiceExpenseRequest = openVoiceExpenseRequest,
            selectedMonth = selectedMonth,
            totalAmount = summary.totalAmount,
            bodyTransition = bodyTransition,
            showFab = showFab,
            showQuickActions = showQuickActions,
            reserveQuickActionsSpace = reserveQuickActionsSpace,
            onOpenCategories = onOpenCategories,
            onOpenAddExpense = onOpenAddExpense,
            onOpenVoiceExpense = onOpenVoiceExpense,
            onOpenCsvTransfer = onOpenCsvTransfer,
            onNavigationDrawerVisibilityChange = onNavigationDrawerVisibilityChange,
            onPreviousMonth = dashboardStore::selectPreviousMonth,
            onNextMonth = dashboardStore::selectNextMonth
        ) { modifier ->
            dashboardBody(modifier)
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            dashboardBody(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            )

            if (showFab) {
                FloatingActionButton(
                    onClick = onOpenAddExpense,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    if (rememberIsIosPlatform()) {
                        Text("+")
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = strings.addExpense
                        )
                    }
                }
            }
        }
    }
}
