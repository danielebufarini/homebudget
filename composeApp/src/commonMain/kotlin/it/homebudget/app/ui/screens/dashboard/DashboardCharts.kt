package it.homebudget.app.ui.screens.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import it.homebudget.app.data.DashboardCardPage
import it.homebudget.app.data.DashboardRecentTransaction
import it.homebudget.app.database.Category
import it.homebudget.app.ui.screens.PlatformCard

private data class DashboardChartPageSpec(
    val page: DashboardCardPage,
    val title: String
)

@Composable
internal fun DashboardCharts(
    modifier: Modifier,
    strings: DashboardStrings,
    balanceChartState: BalanceChartState,
    categoryTotals: List<CategoryTotal>,
    recentTransactions: List<DashboardRecentTransaction>,
    pinnedDashboardCard: DashboardCardPage?,
    onPinDashboardCard: (DashboardCardPage?) -> Unit,
    categoriesById: Map<String, Category>
) {
    val pages = remember(strings.balanceChart, strings.expensesByCategory, strings.recentTransactions) {
        listOf(
            DashboardChartPageSpec(DashboardCardPage.Balance, strings.balanceChart),
            DashboardChartPageSpec(DashboardCardPage.ExpensesByCategory, strings.expensesByCategory),
            DashboardChartPageSpec(DashboardCardPage.RecentTransactions, strings.recentTransactions)
        )
    }
    val initialPage = pages.indexOfFirst { it.page == pinnedDashboardCard }
        .takeIf { it >= 0 }
        ?: 0
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { pages.size }
    )
    val currentPage = pages[pagerState.currentPage.coerceIn(0, pages.lastIndex)]
    val currentPagePinned = currentPage.page == pinnedDashboardCard
    val pinRotationDegrees by animateFloatAsState(
        targetValue = if (currentPagePinned) 0f else 90f,
        animationSpec = tween(durationMillis = 220),
        label = "dashboardPinRotation"
    )

    PlatformCard(modifier = modifier, contentPadding = PaddingValues(0.dp)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(pagerState.pageCount) { index ->
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .background(
                                color = if (pagerState.currentPage == index) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                shape = CircleShape
                            )
                            .size(10.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = currentPage.title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                IconButton(
                    onClick = {
                        onPinDashboardCard(if (currentPagePinned) null else currentPage.page)
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PushPin,
                        contentDescription = if (currentPagePinned) {
                            strings.pinnedDashboardCard
                        } else {
                            strings.pinDashboardCard
                        },
                        modifier = Modifier.rotate(pinRotationDegrees),
                        tint = if (currentPagePinned) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (pages[page].page) {
                    DashboardCardPage.Balance -> BalanceChartPage(strings = strings, state = balanceChartState)
                    DashboardCardPage.ExpensesByCategory -> CategoryBreakdownPage(
                        strings = strings,
                        categoryTotals = categoryTotals,
                        categoriesById = categoriesById
                    )
                    DashboardCardPage.RecentTransactions -> RecentTransactionsPage(
                        strings = strings,
                        transactions = recentTransactions,
                        categoriesById = categoriesById
                    )
                }
            }
        }
    }
}
