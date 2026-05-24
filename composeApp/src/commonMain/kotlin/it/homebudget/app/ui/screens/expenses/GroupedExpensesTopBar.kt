package it.homebudget.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.homebudget.app.data.formatAmount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GroupedExpensesTopBar(
    selectedMonth: MonthCursor,
    title: String,
    navigationDescriptor: String?,
    totalAmount: Long,
    currencySymbol: String,
    isIos: Boolean,
    canAddExpense: Boolean,
    showMonthNavigationControls: Boolean,
    backLabel: String,
    addExpenseLabel: String,
    onBack: () -> Unit,
    onAddExpense: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    CenterAlignedTopAppBar(
        title = {
            GroupedExpensesTopBarTitle(
                selectedMonth = selectedMonth,
                title = title,
                navigationDescriptor = navigationDescriptor,
                totalAmount = totalAmount,
                currencySymbol = currencySymbol,
                showMonthNavigationControls = showMonthNavigationControls,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth
            )
        },
        navigationIcon = {
            if (isIos) {
                TextButton(onClick = onBack) {
                    Text(backLabel)
                }
            }
        },
        actions = {
            if (!isIos && canAddExpense) {
                BottomTransactionQuickActions(
                    addContentDescription = addExpenseLabel,
                    onAddTransaction = onAddExpense,
                    modifier = Modifier.padding(end = 12.dp)
                )
            }
        }
    )
}

@Composable
private fun GroupedExpensesTopBarTitle(
    selectedMonth: MonthCursor,
    title: String,
    navigationDescriptor: String?,
    totalAmount: Long,
    currencySymbol: String,
    showMonthNavigationControls: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    if (navigationDescriptor != null && showMonthNavigationControls) {
        MonthNavigationTitle(
            selectedMonth = selectedMonth,
            subtitle = "$navigationDescriptor • ${formatAmount(totalAmount, currencySymbol)}",
            onPreviousMonth = onPreviousMonth,
            onNextMonth = onNextMonth
        )
        return
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = navigationDescriptor?.let { selectedMonth.label() } ?: title,
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = navigationDescriptor?.let {
                "$it • ${formatAmount(totalAmount, currencySymbol)}"
            } ?: formatAmount(totalAmount, currencySymbol),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
