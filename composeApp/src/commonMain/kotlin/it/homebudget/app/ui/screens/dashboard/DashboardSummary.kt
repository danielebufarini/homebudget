package it.homebudget.app.ui.screens.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ionspin.kotlin.bignum.integer.BigInteger
import it.homebudget.app.data.formatAmount
import it.homebudget.app.database.Category
import it.homebudget.app.localization.rememberCategoryNameResolver
import it.homebudget.app.ui.screens.CategoryLabel
import it.homebudget.app.ui.screens.MonthCursor
import it.homebudget.app.ui.screens.PlatformCard
import it.homebudget.app.ui.screens.rememberIsIosPlatform

@Composable
internal fun ExpenseSummary(
    modifier: Modifier,
    strings: DashboardStrings,
    selectedMonth: MonthCursor,
    summary: MonthlySummary,
    sixMonthSavingsAmount: BigInteger,
    categoriesById: Map<String, Category>,
    onExpensesClick: () -> Unit,
    onIncomeClick: () -> Unit,
    onSharedClick: () -> Unit,
    onHighestDayClick: () -> Unit,
    onTopCategoryClick: () -> Unit
) {
    val isIos = rememberIsIosPlatform()
    val resolveCategoryName = rememberCategoryNameResolver()
    val colorScheme = MaterialTheme.colorScheme
    val topCategory = summary.topCategoryId?.let(categoriesById::get)
    val topCategoryIconKey = topCategory?.icon
    val topCategoryValue = topCategory
        ?.let { resolveCategoryName(it.id, it.name, it.isCustom) }
        ?: "-"
    val highestDayValue = remember(selectedMonth, summary.highestDayOfMonth, strings.weekdayNames) {
        summary.highestDayOfMonth?.let { selectedMonth.toDayLabel(it, strings.weekdayNames) } ?: "-"
    }

    val metricsRows = listOf(
        SummaryMetricUi(
            label = strings.expenses,
            value = summary.expenseCount.toString(),
            containerColor = colorScheme.primaryContainer,
            contentColor = colorScheme.onPrimaryContainer,
            onClick = onExpensesClick
        ),
        SummaryMetricUi(
            label = strings.shared,
            value = formatAmount(summary.sharedAmount, strings.currencySymbol),
            containerColor = colorScheme.secondaryContainer,
            contentColor = colorScheme.onSecondaryContainer,
            onClick = onSharedClick
        ),
        SummaryMetricUi(
            label = strings.income,
            value = formatAmount(summary.incomeAmount, strings.currencySymbol),
            containerColor = colorScheme.tertiaryContainer,
            contentColor = colorScheme.onTertiaryContainer,
            onClick = onIncomeClick
        ),
        SummaryMetricUi(
            label = strings.topCategory,
            value = topCategoryValue,
            valueIconColorKey = summary.topCategoryId,
            valueIconKey = topCategoryIconKey,
            containerColor = colorScheme.errorContainer,
            contentColor = colorScheme.onErrorContainer,
            onClick = if (summary.topCategoryId != null) onTopCategoryClick else null
        ),
        SummaryMetricUi(
            label = strings.highestDay,
            value = highestDayValue,
            containerColor = colorScheme.surfaceVariant,
            contentColor = colorScheme.onSurfaceVariant,
            trailingValue = formatAmount(summary.highestDayAmount, strings.currencySymbol),
            onClick = if (summary.highestDayOfMonth != null) onHighestDayClick else null
        ),
        SummaryMetricUi(
            label = strings.savings,
            value = formatAmount(sixMonthSavingsAmount, strings.currencySymbol),
            containerColor = colorScheme.primary,
            contentColor = colorScheme.onPrimary
        )
    ).chunked(2)

    PlatformCard(
        modifier = modifier,
        contentPadding = PaddingValues(
            horizontal = 16.dp,
            vertical = if (isIos) 14.dp else 16.dp
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(if (isIos) 12.dp else 14.dp)
        ) {
            Text(
                text = strings.monthlySummary,
                style = MaterialTheme.typography.titleLarge
            )

            if (summary.expenseCount == 0) {
                Text(
                    text = strings.noExpensesForMonth,
                    style = if (isIos) MaterialTheme.typography.bodyMedium
                    else MaterialTheme.typography.bodyLarge
                )
            }

            metricsRows.forEach { rowMetrics ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (rowMetrics.size == 1) {
                        val item = rowMetrics.single()
                        SummaryMetric(
                            modifier = Modifier.fillMaxWidth(),
                            label = item.label,
                            value = item.value,
                            valueIconColorKey = item.valueIconColorKey,
                            valueIconKey = item.valueIconKey,
                            containerColor = item.containerColor,
                            contentColor = item.contentColor,
                            trailingValue = item.trailingValue,
                            onClick = item.onClick
                        )
                    } else {
                        rowMetrics.forEach { item ->
                            SummaryMetric(
                                modifier = Modifier.weight(1f),
                                label = item.label,
                                value = item.value,
                                valueIconColorKey = item.valueIconColorKey,
                                valueIconKey = item.valueIconKey,
                                containerColor = item.containerColor,
                                contentColor = item.contentColor,
                                trailingValue = item.trailingValue,
                                onClick = item.onClick
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryMetric(
    modifier: Modifier,
    label: String,
    value: String,
    valueIconColorKey: String?,
    valueIconKey: String?,
    containerColor: Color,
    contentColor: Color,
    trailingValue: String? = null,
    onClick: (() -> Unit)? = null
) {
    val isIos = rememberIsIosPlatform()
    val clickableModifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier

    Card(
        modifier = clickableModifier,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        border = if (isIos) {
            BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
        } else {
            null
        },
        elevation = CardDefaults.cardElevation(defaultElevation = if (isIos) 0.dp else 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(if (isIos) 3.dp else 4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor.copy(alpha = 0.8f)
            )

            if (trailingValue == null) {
                SummaryMetricValue(
                    value = value,
                    valueIconColorKey = valueIconColorKey,
                    valueIconKey = valueIconKey,
                    contentColor = contentColor
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SummaryMetricValue(
                        value = value,
                        valueIconColorKey = valueIconColorKey,
                        valueIconKey = valueIconKey,
                        contentColor = contentColor,
                        modifier = Modifier.fillMaxWidth(0.62f),
                        ellipsize = true
                    )
                    Spacer(modifier = Modifier.width(if (isIos) 10.dp else 12.dp))
                    Text(
                        text = trailingValue,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryMetricValue(
    value: String,
    valueIconColorKey: String?,
    valueIconKey: String?,
    contentColor: Color,
    modifier: Modifier = Modifier,
    ellipsize: Boolean = false
) {
    if (valueIconKey == null) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            modifier = modifier,
            maxLines = if (ellipsize) 1 else Int.MAX_VALUE,
            overflow = if (ellipsize) TextOverflow.Ellipsis else TextOverflow.Clip
        )
    } else {
        CategoryLabel(
            iconKey = valueIconKey,
            colorKey = valueIconColorKey,
            text = value,
            modifier = modifier,
            textStyle = MaterialTheme.typography.titleMedium,
            textColor = contentColor,
            maxLines = 1
        )
    }
}
