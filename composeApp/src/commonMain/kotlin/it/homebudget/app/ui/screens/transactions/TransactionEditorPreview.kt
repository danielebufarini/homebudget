package it.homebudget.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.new_category
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun CategoryPreviewCard(
    name: String,
    iconKey: String,
    modifier: Modifier = Modifier
) {
    val newCategoryLabel = stringResource(Res.string.new_category)
    SoftDepthCard(
        modifier = modifier,
        contentPadding = PaddingValues(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier.size(58.dp),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CategoryIcon(
                        iconKey = iconKey,
                        colorKey = iconKey,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            Text(
                text = name.ifBlank { newCategoryLabel },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun TransactionEditorSkeleton(
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "transactionEditorSkeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeletonAlpha"
    )
    val color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SoftDepthCard(contentPadding = PaddingValues(20.dp)) {
            SkeletonLine(color = color, widthFraction = 0.35f)
            Spacer(Modifier.height(18.dp))
            SkeletonLine(color = color, widthFraction = 0.68f, height = 34.dp)
        }
        repeat(2) {
            SoftDepthCard(contentPadding = PaddingValues(18.dp)) {
                repeat(3) {
                    SkeletonLine(color = color, widthFraction = 0.9f)
                    Spacer(Modifier.height(14.dp))
                }
            }
        }
    }
}

@Composable
private fun SkeletonLine(
    color: Color,
    widthFraction: Float,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 16.dp
) {
    Box(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(color)
    )
}

internal val ExpenseAmountIcon: ImageVector = Icons.Filled.ShoppingCart
internal val IncomeAmountIcon: ImageVector = Icons.Filled.Savings
internal val DescriptionIcon: ImageVector = Icons.AutoMirrored.Filled.Notes
internal val DateIcon: ImageVector = Icons.Filled.CalendarMonth
internal val CategoryRowIcon: ImageVector = Icons.Filled.Category
internal val InstallmentsIcon: ImageVector = Icons.Filled.Payments
internal val RecurringIcon: ImageVector = Icons.Filled.Repeat
internal val SharedIcon: ImageVector = Icons.Filled.Groups
internal val WalletIcon: ImageVector = Icons.Filled.AccountBalanceWallet
