package it.danielebufarini.spesify.ui.screens.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.danielebufarini.spesify.data.DashboardCardPage
import it.danielebufarini.spesify.data.DashboardRecentTransaction
import it.danielebufarini.spesify.database.Category
import it.danielebufarini.spesify.ui.screens.common.MonthCursor

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
    recentTransactionsExpanded: Boolean,
    recentTransactionsFilter: DashboardRecentTransactionFilter,
    pinnedDashboardCard: DashboardCardPage?,
    onPinDashboardCard: (DashboardCardPage?) -> Unit,
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
    onOpenRecentTransaction: (DashboardRecentTransaction) -> Unit
) {
    if (recentTransactionsExpanded) {
        RecentTransactionsPage(
            strings = strings,
            transactions = recentTransactions,
            categoriesById = categoriesById,
            expanded = true,
            filter = recentTransactionsFilter,
            onExpand = onExpandRecentTransactions,
            onCollapse = onCollapseRecentTransactions,
            onFilterChange = onRecentTransactionsFilterChange,
            onOpenTransaction = onOpenRecentTransaction,
            modifier = modifier
        )
        return
    }

    Column(modifier = modifier) {
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
            onExpandRecentTransactions = onExpandRecentTransactions,
            onCollapseRecentTransactions = onCollapseRecentTransactions,
            onRecentTransactionsFilterChange = onRecentTransactionsFilterChange,
            categoriesById = categoriesById,
            onOpenRecentTransaction = onOpenRecentTransaction
        )
    }
}
