package it.homebudget.app.ui.screens.dashboard

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.homebudget.app.data.formatAmount
import it.homebudget.app.database.Category
import it.homebudget.app.localization.rememberCategoryNameResolver
import it.homebudget.app.ui.screens.CategoryLabel

@Composable
internal fun CategoryBreakdownPage(
    strings: DashboardStrings,
    categoryTotals: List<CategoryTotal>,
    categoriesById: Map<String, Category>
) {
    val resolveCategoryName = rememberCategoryNameResolver()

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (categoryTotals.isEmpty()) {
            Text(text = strings.noExpensesForMonth, style = MaterialTheme.typography.bodyLarge)
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(
                items = categoryTotals,
                key = { index, categoryTotal -> categoryTotal.categoryId ?: "unknown-$index" }
            ) { _, categoryTotal ->
                val category = categoryTotal.categoryId?.let(categoriesById::get)
                val categoryName = category
                    ?.let { resolveCategoryName(it.id, it.name, it.isCustom) }
                    ?: strings.unknownCategory
                val categoryIconKey = category?.icon

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
                                    .background(categoryTotal.color, CircleShape)
                            )
                            CategoryLabel(
                                iconKey = categoryIconKey,
                                colorKey = categoryTotal.categoryId,
                                text = categoryName,
                                textStyle = MaterialTheme.typography.bodyLarge,
                                textColor = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                        Text(
                            text = formatAmount(categoryTotal.amount, strings.currencySymbol),
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
                                .fillMaxWidth(categoryTotal.fraction.toFloat())
                                .background(categoryTotal.color, RoundedCornerShape(999.dp))
                        )
                    }
                }
            }
        }
    }
}
