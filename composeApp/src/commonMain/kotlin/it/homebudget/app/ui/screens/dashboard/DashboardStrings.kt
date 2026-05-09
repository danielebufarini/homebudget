package it.homebudget.app.ui.screens.dashboard

import androidx.compose.runtime.Composable
import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.add_expense
import homebudget.composeapp.generated.resources.cash_flow
import homebudget.composeapp.generated.resources.currency_symbol
import homebudget.composeapp.generated.resources.dashboard
import homebudget.composeapp.generated.resources.difference
import homebudget.composeapp.generated.resources.expenses
import homebudget.composeapp.generated.resources.expenses_by_category
import homebudget.composeapp.generated.resources.full_weekday_names
import homebudget.composeapp.generated.resources.highest_day
import homebudget.composeapp.generated.resources.income
import homebudget.composeapp.generated.resources.monthly_summary
import homebudget.composeapp.generated.resources.no_expenses_for_month
import homebudget.composeapp.generated.resources.no_expenses_in_period
import homebudget.composeapp.generated.resources.savings
import homebudget.composeapp.generated.resources.shared
import homebudget.composeapp.generated.resources.short_month_names
import homebudget.composeapp.generated.resources.top_category
import homebudget.composeapp.generated.resources.unknown_category
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

internal data class DashboardStrings(
    val dashboard: String,
    val addExpense: String,
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
    val unknownCategory: String,
    val cashFlow: String,
    val expensesByCategory: String,
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
        addExpense = stringResource(Res.string.add_expense),
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
        unknownCategory = stringResource(Res.string.unknown_category),
        cashFlow = stringResource(Res.string.cash_flow),
        expensesByCategory = stringResource(Res.string.expenses_by_category),
        currencySymbol = stringResource(Res.string.currency_symbol),
        weekdayNames = weekdayNames,
        shortMonthNames = shortMonthNames
    )
}
