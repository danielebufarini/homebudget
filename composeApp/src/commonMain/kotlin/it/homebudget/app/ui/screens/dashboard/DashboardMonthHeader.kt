package it.homebudget.app.ui.screens.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ionspin.kotlin.bignum.integer.BigInteger
import it.homebudget.app.data.formatAmount
import it.homebudget.app.ui.screens.MonthCursor
import it.homebudget.app.ui.screens.MonthNavigationTitle
import it.homebudget.app.ui.screens.PlatformCard

@Composable
internal fun DashboardMonthHeaderCard(
    selectedMonth: MonthCursor,
    totalAmount: BigInteger,
    currencySymbol: String,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    PlatformCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            DashboardMonthHeader(
                selectedMonth = selectedMonth,
                totalAmount = totalAmount,
                currencySymbol = currencySymbol,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth
            )
        }
    }
}

@Composable
internal fun DashboardMonthHeader(
    selectedMonth: MonthCursor,
    totalAmount: BigInteger,
    currencySymbol: String,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    MonthNavigationTitle(
        selectedMonth = selectedMonth,
        subtitle = formatAmount(totalAmount, currencySymbol),
        onPreviousMonth = onPreviousMonth,
        onNextMonth = onNextMonth
    )
}
