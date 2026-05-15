package it.homebudget.app.ui.screens.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.homebudget.app.database.Category
import it.homebudget.app.localization.rememberCategoryNameResolver
import it.homebudget.app.ui.screens.MonthCursor

@Composable
internal fun DashboardBody(
    modifier: Modifier,
    strings: DashboardStrings,
    showMonthHeaderCard: Boolean,
    selectedMonth: MonthCursor,
    summary: MonthlySummary,
    sixMonthSavingsAmount: Long,
    chartState: LineChartState,
    categoriesById: Map<String, Category>,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onOpenMonthlyIncomes: () -> Unit,
    onOpenMonthlyExpenses: () -> Unit,
    onOpenDayExpenses: (Int) -> Unit,
    onOpenSharedExpenses: () -> Unit,
    onOpenCategoryExpenses: (String) -> Unit
) {
    val resolveCategoryName = rememberCategoryNameResolver()

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

        ExpenseSummary(
            modifier = Modifier.fillMaxWidth(),
            strings = strings,
            selectedMonth = selectedMonth,
            summary = summary,
            sixMonthSavingsAmount = sixMonthSavingsAmount,
            categoriesById = categoriesById,
            onExpensesClick = onOpenMonthlyExpenses,
            onIncomeClick = onOpenMonthlyIncomes,
            onSharedClick = onOpenSharedExpenses,
            onHighestDayClick = {
                summary.highestDayOfMonth?.let(onOpenDayExpenses)
            },
            onTopCategoryClick = {
                summary.topCategoryId
                    ?.let { categoriesById[it] }
                    ?.let { onOpenCategoryExpenses(resolveCategoryName(it.id, it.name)) }
            }
        )

        Spacer(Modifier.height(16.dp))

        DashboardCharts(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            strings = strings,
            lineChartState = chartState,
            categoryTotals = summary.categoryTotals,
            categoriesById = categoriesById
        )
    }
}
