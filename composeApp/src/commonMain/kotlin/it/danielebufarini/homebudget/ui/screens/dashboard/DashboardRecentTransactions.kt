package it.danielebufarini.homebudget.ui.screens.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.danielebufarini.homebudget.data.DashboardRecentTransaction
import it.danielebufarini.homebudget.data.DashboardRecentTransactionType
import it.danielebufarini.homebudget.data.formatAmount
import it.danielebufarini.homebudget.data.subtractAmountsExact
import it.danielebufarini.homebudget.database.Category
import it.danielebufarini.homebudget.localization.rememberCategoryNameResolver
import it.danielebufarini.homebudget.ui.screens.categories.CategoryIcon
import it.danielebufarini.homebudget.ui.screens.expenses.epochMillisToLocalDate
import it.danielebufarini.homebudget.ui.screens.expenses.formatExpenseDateGroupTitle

@Composable
internal fun RecentTransactionsPage(
    strings: DashboardStrings,
    transactions: List<DashboardRecentTransaction>,
    categoriesById: Map<String, Category>
) {
    if (transactions.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = strings.noRecentTransactions,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        transactions.forEach { transaction ->
            RecentTransactionCard(
                transaction = transaction,
                strings = strings,
                category = transaction.categoryId?.let(categoriesById::get)
            )
        }
    }
}

@Composable
private fun RecentTransactionCard(
    transaction: DashboardRecentTransaction,
    strings: DashboardStrings,
    category: Category?
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
