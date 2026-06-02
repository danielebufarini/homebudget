package it.danielebufarini.homebudget.ui.screens.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.danielebufarini.homebudget.ui.screens.categories.CategoryIcon

@Composable
fun ExpenseListItemRow(
    title: String,
    subtitleText: String,
    amountText: String,
    categoryIconKey: String? = null,
    categoryColorKey: String? = null,
    isRecurring: Boolean = false,
    subtitleFontSizeOffsetSp: Int = 0,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.72f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (categoryIconKey != null) {
                    CategoryIcon(
                        iconKey = categoryIconKey,
                        modifier = Modifier.size(18.dp),
                        contentDescription = null,
                        colorKey = categoryColorKey
                    )
                }
                if (isRecurring) {
                    RecurringBadge()
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = (MaterialTheme.typography.bodySmall.fontSize.value + subtitleFontSizeOffsetSp).sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = amountText,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
    }
}

@Composable
private fun RecurringBadge(
    horizontalPadding: Dp = 6.dp,
    verticalPadding: Dp = 2.dp
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.error)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "R",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onError
        )
    }
}
