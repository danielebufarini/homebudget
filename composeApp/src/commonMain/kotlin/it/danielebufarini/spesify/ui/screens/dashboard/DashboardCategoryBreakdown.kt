package it.danielebufarini.spesify.ui.screens.dashboard

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import it.danielebufarini.spesify.data.formatAmount
import it.danielebufarini.spesify.database.Category
import it.danielebufarini.spesify.localization.rememberCategoryNameResolver
import it.danielebufarini.spesify.ui.screens.categories.CategoryLabel

@Composable
internal fun CategoryBreakdownPage(
    strings: DashboardStrings,
    categoryTotals: List<CategoryTotal>,
    categoriesById: Map<String, Category>,
    expanded: Boolean,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onOpenCategoryTransactions: (String?, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val resolveCategoryName = rememberCategoryNameResolver()
    val rows = remember(categoryTotals, categoriesById, resolveCategoryName, strings.unknownCategory) {
        categoryTotals.mapIndexed { index, categoryTotal ->
            val category = categoryTotal.categoryId?.let(categoriesById::get)
            CategoryBreakdownRow(
                key = categoryTotal.categoryId ?: "unknown-$index",
                categoryId = categoryTotal.categoryId,
                categoryIconKey = category?.icon,
                categoryName = category
                    ?.let { resolveCategoryName(it.id, it.name) }
                    ?: strings.unknownCategory,
                amount = categoryTotal.amount,
                fraction = categoryTotal.fraction,
                color = categoryTotal.color
            )
        }
    }
    val visibleRows = remember(rows, expanded) {
        if (expanded) {
            rows
        } else {
            rows.take(DASHBOARD_CATEGORY_BREAKDOWN_COLLAPSED_LIMIT)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (expanded) {
            CategoryBreakdownControls(
                strings = strings,
                expanded = true,
                onExpand = onExpand,
                onCollapse = onCollapse
            )
        }

        if (visibleRows.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = strings.noExpensesForMonth,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 4.dp)
        ) {
            items(
                items = visibleRows,
                key = CategoryBreakdownRow::key,
                contentType = { "dashboard-category-breakdown-row" }
            ) { row ->
                Column(
                    modifier = Modifier.clickable(
                        role = Role.Button,
                        onClick = {
                            onOpenCategoryTransactions(row.categoryId, row.categoryName)
                        }
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(row.color, CircleShape)
                            )
                            CategoryLabel(
                                iconKey = row.categoryIconKey,
                                colorKey = row.categoryId,
                                text = row.categoryName,
                                textStyle = MaterialTheme.typography.bodyLarge,
                                textColor = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                        Text(
                            text = formatAmount(row.amount, strings.currencySymbol),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(999.dp)
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(row.fraction.toFloat())
                                .background(row.color, RoundedCornerShape(999.dp))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryBreakdownControls(
    strings: DashboardStrings,
    expanded: Boolean,
    onExpand: () -> Unit,
    onCollapse: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = if (expanded) onCollapse else onExpand,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = strings.collapseCategoryBreakdown
            )
        }
    }
}

private data class CategoryBreakdownRow(
    val key: String,
    val categoryId: String?,
    val categoryIconKey: String?,
    val categoryName: String,
    val amount: Long,
    val fraction: Double,
    val color: Color
)
