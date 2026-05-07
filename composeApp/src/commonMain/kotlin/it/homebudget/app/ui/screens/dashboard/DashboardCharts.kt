package it.homebudget.app.ui.screens.dashboard

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import it.homebudget.app.database.Category
import it.homebudget.app.ui.screens.PlatformCard

@Composable
internal fun DashboardCharts(
    modifier: Modifier,
    strings: DashboardStrings,
    lineChartState: LineChartState,
    categoryTotals: List<CategoryTotal>,
    categoriesById: Map<String, Category>
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val pageTitles = remember(strings.cashFlow, strings.expensesByCategory) {
        listOf(strings.cashFlow, strings.expensesByCategory)
    }

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

            Text(
                text = pageTitles[pagerState.currentPage],
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (page) {
                    0 -> LineChartPage(strings = strings, state = lineChartState)
                    else -> CategoryBreakdownPage(
                        strings = strings,
                        categoryTotals = categoryTotals,
                        categoriesById = categoriesById
                    )
                }
            }
        }
    }
}
