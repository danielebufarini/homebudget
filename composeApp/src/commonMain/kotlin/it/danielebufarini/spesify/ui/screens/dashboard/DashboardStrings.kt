package it.danielebufarini.spesify.ui.screens.dashboard

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import spesify.composeapp.generated.resources.Res
import spesify.composeapp.generated.resources.add_expense
import spesify.composeapp.generated.resources.balance_chart
import spesify.composeapp.generated.resources.cumulative_balance
import spesify.composeapp.generated.resources.currency_symbol
import spesify.composeapp.generated.resources.dashboard
import spesify.composeapp.generated.resources.dashboard_search_placeholder
import spesify.composeapp.generated.resources.difference
import spesify.composeapp.generated.resources.expenses
import spesify.composeapp.generated.resources.expenses_by_category
import spesify.composeapp.generated.resources.full_weekday_names
import spesify.composeapp.generated.resources.highest_day
import spesify.composeapp.generated.resources.income
import spesify.composeapp.generated.resources.monthly_summary
import spesify.composeapp.generated.resources.no_expenses_for_month
import spesify.composeapp.generated.resources.no_expenses_in_period
import spesify.composeapp.generated.resources.no_recent_transactions
import spesify.composeapp.generated.resources.pin_dashboard_card
import spesify.composeapp.generated.resources.pinned_dashboard_card
import spesify.composeapp.generated.resources.recent_transactions
import spesify.composeapp.generated.resources.savings
import spesify.composeapp.generated.resources.search_transactions
import spesify.composeapp.generated.resources.shared
import spesify.composeapp.generated.resources.short_month_names
import spesify.composeapp.generated.resources.this_month
import spesify.composeapp.generated.resources.top_category
import spesify.composeapp.generated.resources.unknown_category
import spesify.composeapp.generated.resources.voice_expense

internal data class DashboardStrings(
    val dashboard: String,
    val dashboardSearchPlaceholder: String,
    val searchTransactions: String,
    val addExpense: String,
    val voiceExpense: String,
    val expenses: String,
    val shared: String,
    val income: String,
    val difference: String,
    val savings: String,
    val topCategory: String,
    val highestDay: String,
    val monthlySummary: String,
    val noExpensesForMonth: String,
    val noExpensesInPeriod: String,
    val recentTransactions: String,
    val noRecentTransactions: String,
    val pinDashboardCard: String,
    val pinnedDashboardCard: String,
    val unknownCategory: String,
    val balanceChart: String,
    val expensesByCategory: String,
    val cumulativeBalance: String,
    val thisMonth: String,
    val currencySymbol: String,
    val weekdayNames: List<String>,
    val shortMonthNames: List<String>
)

@Composable
internal fun rememberDashboardStrings(): DashboardStrings {
    val weekdayNames = stringArrayResource(Res.array.full_weekday_names).toList()
    val shortMonthNames = stringArrayResource(Res.array.short_month_names).toList()

    return DashboardStrings(
        dashboard = stringResource(Res.string.dashboard),
        dashboardSearchPlaceholder = stringResource(Res.string.dashboard_search_placeholder),
        searchTransactions = stringResource(Res.string.search_transactions),
        addExpense = stringResource(Res.string.add_expense),
        voiceExpense = stringResource(Res.string.voice_expense),
        expenses = stringResource(Res.string.expenses),
        shared = stringResource(Res.string.shared),
        income = stringResource(Res.string.income),
        difference = stringResource(Res.string.difference),
        savings = stringResource(Res.string.savings),
        topCategory = stringResource(Res.string.top_category),
        highestDay = stringResource(Res.string.highest_day),
        monthlySummary = stringResource(Res.string.monthly_summary),
        noExpensesForMonth = stringResource(Res.string.no_expenses_for_month),
        noExpensesInPeriod = stringResource(Res.string.no_expenses_in_period),
        recentTransactions = stringResource(Res.string.recent_transactions),
        noRecentTransactions = stringResource(Res.string.no_recent_transactions),
        pinDashboardCard = stringResource(Res.string.pin_dashboard_card),
        pinnedDashboardCard = stringResource(Res.string.pinned_dashboard_card),
        unknownCategory = stringResource(Res.string.unknown_category),
        balanceChart = stringResource(Res.string.balance_chart),
        expensesByCategory = stringResource(Res.string.expenses_by_category),
        cumulativeBalance = stringResource(Res.string.cumulative_balance),
        thisMonth = stringResource(Res.string.this_month),
        currencySymbol = stringResource(Res.string.currency_symbol),
        weekdayNames = weekdayNames,
        shortMonthNames = shortMonthNames
    )
}
