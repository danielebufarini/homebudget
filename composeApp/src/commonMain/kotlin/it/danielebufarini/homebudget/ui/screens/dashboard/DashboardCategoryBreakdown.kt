package it.danielebufarini.homebudget.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import it.danielebufarini.homebudget.data.formatAmount
import it.danielebufarini.homebudget.database.Category
import it.danielebufarini.homebudget.localization.rememberCategoryNameResolver
import it.danielebufarini.homebudget.ui.screens.categories.CategoryLabel

@Composable
internal fun CategoryBreakdownPage(
    strings: DashboardStrings,
    categoryTotals: List<CategoryTotal>,
    categoriesById: Map<String, Category>
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

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (rows.isEmpty()) {
            Text(text = strings.noExpensesForMonth, style = MaterialTheme.typography.bodyLarge)
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = rows,
                key = CategoryBreakdownRow::key,
                contentType = { "dashboard-category-breakdown-row" }
            ) { row ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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

private data class CategoryBreakdownRow(
    val key: String,
    val categoryId: String?,
    val categoryIconKey: String?,
    val categoryName: String,
    val amount: Long,
    val fraction: Double,
    val color: Color
)
