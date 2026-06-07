package it.danielebufarini.spesify.ui.screens.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.danielebufarini.spesify.data.formatAmount
import it.danielebufarini.spesify.ui.screens.common.MonthCursor
import it.danielebufarini.spesify.ui.screens.common.MonthNavigationTitle
import it.danielebufarini.spesify.ui.screens.platform.PlatformCard

@Composable
internal fun DashboardMonthHeaderCard(
    selectedMonth: MonthCursor,
    totalAmount: Long,
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
    totalAmount: Long,
    currencySymbol: String,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier,
    useIosGlassStyle: Boolean = false
) {
    MonthNavigationTitle(
        selectedMonth = selectedMonth,
        subtitle = formatAmount(totalAmount, currencySymbol),
        onPreviousMonth = onPreviousMonth,
        onNextMonth = onNextMonth,
        modifier = modifier,
        useIosGlassStyle = useIosGlassStyle
    )
}
