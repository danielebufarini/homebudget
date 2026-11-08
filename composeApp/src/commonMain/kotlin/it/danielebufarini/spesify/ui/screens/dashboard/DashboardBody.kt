package it.danielebufarini.spesify.ui.screens.dashboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.spring
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import it.danielebufarini.spesify.data.DashboardCardPage
import it.danielebufarini.spesify.data.DashboardRecentTransaction
import it.danielebufarini.spesify.database.Category
import it.danielebufarini.spesify.ui.screens.common.MonthCursor
import it.danielebufarini.spesify.ui.screens.platform.PlatformCard

internal enum class DashboardBodyMode {
    Standard,
    BalanceChartExpanded,
    CategoryBreakdownExpanded,
    RecentTransactionsExpanded
}

@Composable
internal fun DashboardBody(
    modifier: Modifier,
    strings: DashboardStrings,
    showMonthHeaderCard: Boolean,
    selectedMonth: MonthCursor,
    summary: MonthlySummary,
    monthlySavingsAmount: Long,
    recurringTotalAmount: Long,
    chartState: BalanceChartState,
    recentTransactions: List<DashboardRecentTransaction>,
    transition: Transition<DashboardBodyMode>,
    recentTransactionsFilter: DashboardRecentTransactionFilter,
    hideFutureExpenses: Boolean,
    onHideFutureExpensesChange: (Boolean) -> Unit,
    pinnedDashboardCard: DashboardCardPage?,
    onPinDashboardCard: (DashboardCardPage?) -> Unit,
    onExpandBalanceChart: () -> Unit,
    onCollapseBalanceChart: () -> Unit,
    onExpandCategoryBreakdown: () -> Unit,
    onCollapseCategoryBreakdown: () -> Unit,
    onExpandRecentTransactions: () -> Unit,
    onCollapseRecentTransactions: () -> Unit,
    onRecentTransactionsFilterChange: (DashboardRecentTransactionFilter) -> Unit,
    categoriesById: Map<String, Category>,
    showTransactionSearch: Boolean,
    searchQuery: String,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onOpenMonthlyIncomes: () -> Unit,
    onOpenMonthlyExpenses: () -> Unit,
    onOpenDayExpenses: (Int) -> Unit,
    onOpenSharedExpenses: () -> Unit,
    onOpenRecurringExpenses: () -> Unit,
    onOpenCategoryTransactions: (String?, String) -> Unit,
    onOpenRecentTransaction: (DashboardRecentTransaction) -> Unit
) {
    val pages = DashboardCardPage.entries
    val pagerState = rememberPagerState(
        initialPage = pages.indexOf(pinnedDashboardCard).coerceAtLeast(0),
        pageCount = { pages.size }
    )
    val scrollState = rememberScrollState()
    var compactViewportHeight by remember { mutableIntStateOf(0) }

    SharedTransitionLayout(modifier = modifier) {
        transition.AnimatedContent(
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                (EnterTransition.None togetherWith ExitTransition.None).using(null)
            },
        ) { mode ->
            val card = when (mode) {
                DashboardBodyMode.Standard -> pages[pagerState.currentPage]
                DashboardBodyMode.BalanceChartExpanded -> DashboardCardPage.Balance
                DashboardBodyMode.CategoryBreakdownExpanded -> DashboardCardPage.ExpensesByCategory
                DashboardBodyMode.RecentTransactionsExpanded -> DashboardCardPage.RecentTransactions
            }
            val cardModifier = Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(card),
                animatedVisibilityScope = this@AnimatedContent,
                boundsTransform = { _, _ ->
                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                },
                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds
            ).testTag(if (mode == DashboardBodyMode.Standard) "dashboard_compact_card" else "dashboard_expanded_card")
            if (mode != DashboardBodyMode.Standard) {
                PlatformCard(modifier = cardModifier.fillMaxSize(), contentPadding = PaddingValues(0.dp)) {
                    when (mode) {
                        DashboardBodyMode.BalanceChartExpanded -> BalanceChartPage(
                            strings = strings,
                            state = chartState,
                            expanded = true,
                            onCollapse = onCollapseBalanceChart
                        )

                        DashboardBodyMode.CategoryBreakdownExpanded -> CategoryBreakdownPage(
                            strings = strings,
                            categoryTotals = summary.categoryTotals,
                            categoriesById = categoriesById,
                            expanded = true,
                            onExpand = onExpandCategoryBreakdown,
                            onCollapse = onCollapseCategoryBreakdown,
                            onOpenCategoryTransactions = onOpenCategoryTransactions,
                            modifier = Modifier.fillMaxSize()
                        )

                        DashboardBodyMode.RecentTransactionsExpanded -> RecentTransactionsPage(
                            strings = strings,
                            transactions = recentTransactions,
                            categoriesById = categoriesById,
                            expanded = true,
                            filter = recentTransactionsFilter,
                            hideFutureExpenses = hideFutureExpenses,
                            onHideFutureExpensesChange = onHideFutureExpensesChange,
                            onExpand = onExpandRecentTransactions,
                            onCollapse = onCollapseRecentTransactions,
                            onFilterChange = onRecentTransactionsFilterChange,
                            onOpenTransaction = onOpenRecentTransaction,
                            modifier = Modifier.fillMaxSize()
                        )

                        DashboardBodyMode.Standard -> Unit
                    }
                }
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val settled = transition.currentState == DashboardBodyMode.Standard &&
                        transition.targetState == DashboardBodyMode.Standard && !transition.isRunning
                    SideEffect {
                        if (settled) compactViewportHeight = constraints.maxHeight
                    }
                    // Keep the scroll viewport stable while the top bar enters/exits; otherwise
                    // ScrollState clamps its offset and the card returns to a different position.
                    val viewportHeight = if (!settled && compactViewportHeight > 0) {
                        minOf(constraints.maxHeight, compactViewportHeight)
                    } else {
                        constraints.maxHeight
                    }
                    val viewportMaxHeight = with(LocalDensity.current) { viewportHeight.toDp() }
                    Layout(
                        modifier = Modifier.heightIn(max = viewportMaxHeight).fillMaxSize().verticalScroll(scrollState),
                        content = {
                            Column {
                                if (showMonthHeaderCard) {
                                    DashboardMonthHeaderCard(
                                        selectedMonth = selectedMonth,
                                        totalAmount = summary.totalAmount,
                                        currencySymbol = strings.currencySymbol,
                                        onPreviousMonth = onPreviousMonth,
                                        onNextMonth = onNextMonth
                                    )
                                    Spacer(Modifier.height(16.dp))
                                }

                                if (showTransactionSearch) {
                                    DashboardSearchBar(
                                        query = searchQuery,
                                        placeholder = strings.dashboardSearchPlaceholder,
                                        searchContentDescription = strings.searchTransactions,
                                        onQueryChange = onSearchQueryChange,
                                        onSearch = onSearchSubmit,
                                        modifier = Modifier.fillMaxWidth(),
                                    )

                                    Spacer(Modifier.height(16.dp))
                                }

                                ExpenseSummary(
                                    modifier = Modifier.fillMaxWidth(),
                                    strings = strings,
                                    selectedMonth = selectedMonth,
                                    summary = summary,
                                    monthlySavingsAmount = monthlySavingsAmount,
                                    recurringTotalAmount = recurringTotalAmount,
                                    onExpensesClick = onOpenMonthlyExpenses,
                                    onIncomeClick = onOpenMonthlyIncomes,
                                    onSharedClick = onOpenSharedExpenses,
                                    onHighestDayClick = {
                                        summary.highestDayOfMonth?.let(onOpenDayExpenses)
                                    },
                                    onRecurringClick = onOpenRecurringExpenses
                                )

                                Spacer(Modifier.height(16.dp))
                            }

                            DashboardCharts(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                cardModifier = cardModifier,
                                pagerState = pagerState,
                                strings = strings,
                                balanceChartState = chartState,
                                categoryTotals = summary.categoryTotals,
                                recentTransactions = recentTransactions,
                                recentTransactionsFilter = recentTransactionsFilter,
                                hideFutureExpenses = hideFutureExpenses,
                                onHideFutureExpensesChange = onHideFutureExpensesChange,
                                pinnedDashboardCard = pinnedDashboardCard,
                                onPinDashboardCard = onPinDashboardCard,
                                onExpandBalanceChart = onExpandBalanceChart,
                                onCollapseBalanceChart = onCollapseBalanceChart,
                                onExpandCategoryBreakdown = onExpandCategoryBreakdown,
                                onCollapseCategoryBreakdown = onCollapseCategoryBreakdown,
                                onExpandRecentTransactions = onExpandRecentTransactions,
                                onCollapseRecentTransactions = onCollapseRecentTransactions,
                                onRecentTransactionsFilterChange = onRecentTransactionsFilterChange,
                                categoriesById = categoriesById,
                                onOpenCategoryTransactions = onOpenCategoryTransactions,
                                onOpenRecentTransaction = onOpenRecentTransaction
                            )
                        }
                    ) { measurables, constraints ->
                        val header = measurables[0].measure(
                            constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
                        )
                        val cardHeight = (viewportHeight - header.height).coerceAtLeast(240.dp.roundToPx())
                        val charts = measurables[1].measure(
                            Constraints.fixed(width = constraints.maxWidth, height = cardHeight)
                        )
                        layout(constraints.maxWidth, header.height + charts.height) {
                            header.placeRelative(0, 0)
                            charts.placeRelative(0, header.height)
                        }
                    }
                }
            }
        }
    }
}
