package it.danielebufarini.spesify.ui.screens.dashboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.danielebufarini.spesify.data.DashboardCardPage
import it.danielebufarini.spesify.data.DashboardRecentTransaction
import it.danielebufarini.spesify.database.Category
import it.danielebufarini.spesify.ui.screens.common.MonthCursor

private enum class DashboardBodyMode {
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
    balanceChartExpanded: Boolean,
    categoryBreakdownExpanded: Boolean,
    recentTransactionsExpanded: Boolean,
    recentTransactionsFilter: DashboardRecentTransactionFilter,
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
    val bodyMode = when {
        balanceChartExpanded -> DashboardBodyMode.BalanceChartExpanded
        categoryBreakdownExpanded -> DashboardBodyMode.CategoryBreakdownExpanded
        recentTransactionsExpanded -> DashboardBodyMode.RecentTransactionsExpanded
        else -> DashboardBodyMode.Standard
    }
    LockDashboardOrientation(landscape = bodyMode == DashboardBodyMode.BalanceChartExpanded)

    AnimatedContent(
        targetState = bodyMode,
        modifier = modifier,
        transitionSpec = {
            (
                fadeIn(animationSpec = tween(durationMillis = 180, delayMillis = 60)) +
                    scaleIn(
                        initialScale = 0.98f,
                        animationSpec = tween(durationMillis = 260)
                    )
                ) togetherWith (
                    fadeOut(animationSpec = tween(durationMillis = 140)) +
                        scaleOut(
                            targetScale = 0.98f,
                            animationSpec = tween(durationMillis = 180)
                        )
                    )
        },
        label = "dashboardBodyMode"
    ) { mode ->
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
                onExpand = onExpandRecentTransactions,
                onCollapse = onCollapseRecentTransactions,
                onFilterChange = onRecentTransactionsFilterChange,
                onOpenTransaction = onOpenRecentTransaction,
                modifier = Modifier.fillMaxSize()
            )

            DashboardBodyMode.Standard -> Column(modifier = Modifier.fillMaxSize()) {
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

                DashboardCharts(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    strings = strings,
                    balanceChartState = chartState,
                    categoryTotals = summary.categoryTotals,
                    recentTransactions = recentTransactions,
                    recentTransactionsFilter = recentTransactionsFilter,
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
        }
    }
}
