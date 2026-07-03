package it.danielebufarini.spesify.ui.screens.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.danielebufarini.spesify.data.DashboardRecentTransaction
import it.danielebufarini.spesify.data.DashboardRecentTransactionType
import it.danielebufarini.spesify.data.formatAmount
import it.danielebufarini.spesify.data.subtractAmountsExact
import it.danielebufarini.spesify.database.Category
import it.danielebufarini.spesify.localization.rememberCategoryNameResolver
import it.danielebufarini.spesify.ui.screens.categories.CategoryIcon
import it.danielebufarini.spesify.ui.screens.expenses.epochMillisToLocalDate
import it.danielebufarini.spesify.ui.screens.expenses.formatExpenseDateGroupTitle
import it.danielebufarini.spesify.ui.screens.platform.rememberIsIosPlatform

internal enum class DashboardRecentTransactionFilter {
    All,
    Income,
    Expenses
}

@Composable
internal fun RecentTransactionsPage(
    strings: DashboardStrings,
    transactions: List<DashboardRecentTransaction>,
    categoriesById: Map<String, Category>,
    expanded: Boolean,
    filter: DashboardRecentTransactionFilter,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onFilterChange: (DashboardRecentTransactionFilter) -> Unit,
    onOpenTransaction: (DashboardRecentTransaction) -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleTransactions = remember(transactions, expanded, filter) {
        val limitedTransactions = transactions.take(
            if (expanded) {
                DASHBOARD_RECENT_TRANSACTIONS_EXPANDED_LIMIT
            } else {
                DASHBOARD_RECENT_TRANSACTIONS_COLLAPSED_LIMIT
            }
        )
        when {
            !expanded -> limitedTransactions
            filter == DashboardRecentTransactionFilter.All -> limitedTransactions
            filter == DashboardRecentTransactionFilter.Income -> {
                limitedTransactions.filter { transaction ->
                    transaction.type == DashboardRecentTransactionType.Income
                }
            }
            else -> {
                limitedTransactions.filter { transaction ->
                    transaction.type == DashboardRecentTransactionType.Expense
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (expanded) {
            RecentTransactionsControls(
                strings = strings,
                expanded = true,
                filter = filter,
                onExpand = onExpand,
                onCollapse = onCollapse,
                onFilterChange = onFilterChange
            )
        }

        if (visibleTransactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = strings.noRecentTransactions,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 4.dp)
            ) {
                items(
                    items = visibleTransactions,
                    key = { transaction -> "${transaction.type}:${transaction.id}" },
                    contentType = { "dashboard-recent-transaction" }
                ) { transaction ->
                    RecentTransactionCard(
                        transaction = transaction,
                        strings = strings,
                        category = transaction.categoryId?.let(categoriesById::get),
                        onOpenTransaction = onOpenTransaction
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentTransactionsControls(
    strings: DashboardStrings,
    expanded: Boolean,
    filter: DashboardRecentTransactionFilter,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onFilterChange: (DashboardRecentTransactionFilter) -> Unit
) {
    val isIos = rememberIsIosPlatform()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RecentTransactionsFilterControl(
            strings = strings,
            selectedFilter = filter,
            onFilterChange = onFilterChange,
            useIosGlassStyle = isIos,
            modifier = Modifier.weight(1f)
        )

        IconButton(
            onClick = if (expanded) onCollapse else onExpand,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = strings.collapseRecentTransactions
            )
        }
    }
}

@Composable
private fun RecentTransactionsFilterControl(
    strings: DashboardStrings,
    selectedFilter: DashboardRecentTransactionFilter,
    onFilterChange: (DashboardRecentTransactionFilter) -> Unit,
    useIosGlassStyle: Boolean,
    modifier: Modifier = Modifier
) {
    val options = remember(strings.allTransactions, strings.income, strings.expenses) {
        listOf(
            DashboardRecentTransactionFilter.All to strings.allTransactions,
            DashboardRecentTransactionFilter.Income to strings.income,
            DashboardRecentTransactionFilter.Expenses to strings.expenses
        )
    }

    if (useIosGlassStyle) {
        IosRecentTransactionsFilterControl(
            options = options,
            selectedFilter = selectedFilter,
            onFilterChange = onFilterChange,
            modifier = modifier
        )
    } else {
        AndroidRecentTransactionsFilterButtonGroup(
            options = options,
            selectedFilter = selectedFilter,
            onFilterChange = onFilterChange,
            modifier = modifier
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun AndroidRecentTransactionsFilterButtonGroup(
    options: List<Pair<DashboardRecentTransactionFilter, String>>,
    selectedFilter: DashboardRecentTransactionFilter,
    onFilterChange: (DashboardRecentTransactionFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    ButtonGroup(
        overflowIndicator = { menuState ->
            ButtonGroupDefaults.OverflowIndicator(menuState)
        },
        modifier = modifier,
        expandedRatio = 0f,
        horizontalArrangement = ButtonGroupDefaults.HorizontalArrangement
    ) {
        options.forEach { (filter, label) ->
            toggleableItem(
                checked = selectedFilter == filter,
                label = label,
                onCheckedChange = { onFilterChange(filter) },
                weight = 1f
            )
        }
    }
}

@Composable
private fun IosRecentTransactionsFilterControl(
    options: List<Pair<DashboardRecentTransactionFilter, String>>,
    selectedFilter: DashboardRecentTransactionFilter,
    onFilterChange: (DashboardRecentTransactionFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.42f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEach { (filter, label) ->
                IosRecentTransactionsFilterSegment(
                    label = label,
                    selected = selectedFilter == filter,
                    onClick = { onFilterChange(filter) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun IosRecentTransactionsFilterSegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = 180),
        label = "recentTransactionsFilterContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.74f)
        },
        animationSpec = tween(durationMillis = 180),
        label = "recentTransactionsFilterContent"
    )

    Surface(
        modifier = modifier
            .defaultMinSize(minHeight = 40.dp)
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick
            ),
        shape = RoundedCornerShape(19.dp),
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 0.dp,
        shadowElevation = if (selected) 6.dp else 0.dp,
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f))
        } else {
            null
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RecentTransactionCard(
    transaction: DashboardRecentTransaction,
    strings: DashboardStrings,
    category: Category?,
    onOpenTransaction: (DashboardRecentTransaction) -> Unit
) {
    val isIncome = transaction.type == DashboardRecentTransactionType.Income
    val accent = if (isIncome) Color(0xFF22A06B) else Color(0xFFD65A5A)
    val categoryNameResolver = rememberCategoryNameResolver()
    val categoryLabel = category?.let { categoryNameResolver(it.id, it.name) } ?: strings.unknownCategory
    val title = transaction.description?.trim()?.takeIf(String::isNotEmpty) ?: categoryLabel
    val dateLabel = formatExpenseDateGroupTitle(
        date = epochMillisToLocalDate(transaction.date),
        shortMonthNames = strings.shortMonthNames
    )
    val signedAmount = if (isIncome) transaction.amount else subtractAmountsExact(0L, transaction.amount)
    val iconBrush = remember(accent) {
        Brush.linearGradient(
            colors = listOf(
                accent.copy(alpha = 0.22f),
                accent.copy(alpha = 0.08f)
            )
        )
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenTransaction(transaction) }
            .border(
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                shape = RoundedCornerShape(22.dp)
            ),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TransactionIcon(
                category = category,
                accent = accent,
                iconBrush = iconBrush,
                isIncome = isIncome
            )

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "$dateLabel · $categoryLabel",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(8.dp))

            Text(
                text = formatAmount(signedAmount, strings.currencySymbol),
                color = accent,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TransactionIcon(
    category: Category?,
    accent: Color,
    iconBrush: Brush,
    isIncome: Boolean
) {
    Surface(
        modifier = Modifier.size(38.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        shadowElevation = 4.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(iconBrush),
            contentAlignment = Alignment.Center
        ) {
            if (category != null) {
                CategoryIcon(
                    iconKey = category.icon,
                    colorKey = category.id,
                    tint = accent,
                    modifier = Modifier.size(21.dp)
                )
            } else {
                Icon(
                    imageVector = if (isIncome) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
