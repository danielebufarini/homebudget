package it.danielebufarini.spesify.ui.screens.categories.management

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.danielebufarini.spesify.database.CATEGORY_TYPE_INCOME
import org.jetbrains.compose.resources.stringResource
import spesify.composeapp.generated.resources.Res
import spesify.composeapp.generated.resources.categories_archived_badge
import spesify.composeapp.generated.resources.delete
import spesify.composeapp.generated.resources.edit_category
import spesify.composeapp.generated.resources.expense_label
import spesify.composeapp.generated.resources.income_label

internal fun LazyGridScope.categoryCardItems(
    categories: List<CategoryUiModel>,
    onEdit: (CategoryUiModel) -> Unit,
    onDelete: (CategoryUiModel) -> Unit,
) {
    items(
        items = categories,
        key = { it.id },
        contentType = { category -> "category-card:${category.categoryType}:${category.isArchived}" },
    ) { category ->
        CategoryOverviewCard(
            category = category,
            onEdit = { onEdit(category) },
            onDelete = { onDelete(category) },
        )
    }
}

@Composable
internal fun CategoryOverviewCard(
    category: CategoryUiModel,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val palette = rememberCategoriesPalette()
    val editLabel = stringResource(Res.string.edit_category)
    val deleteLabel = stringResource(Res.string.delete)
    val archivedLabel = stringResource(Res.string.categories_archived_badge)
    val iconBrush = remember(category.accent, palette.isDark) {
        Brush.linearGradient(
            colors = listOf(
                category.accent.copy(alpha = if (palette.isDark) 0.40f else 0.24f),
                category.accent.copy(alpha = if (palette.isDark) 0.18f else 0.08f),
            ),
        )
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                border = BorderStroke(
                    width = if (category.isArchived) 1.dp else 1.25.dp,
                    color = if (category.isArchived) {
                        palette.cardBorder.copy(alpha = 0.65f)
                    } else {
                        category.accent.copy(alpha = if (palette.isDark) 0.30f else 0.18f)
                    },
                ),
                shape = RoundedCornerShape(30.dp),
            ),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = palette.cardSurface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            CategoryCardHeader(
                category = category,
                iconBrush = iconBrush,
                archivedLabel = archivedLabel,
            )
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .padding(horizontal = 20.dp)
                    .background(palette.divider),
            )
            CategoryCardActions(
                editLabel = editLabel,
                deleteLabel = deleteLabel,
                editTint = if (category.isArchived) palette.textSecondary else category.accent,
                onEdit = onEdit,
                onDelete = onDelete,
            )
        }
    }
}

@Composable
private fun CategoryCardHeader(
    category: CategoryUiModel,
    iconBrush: Brush,
    archivedLabel: String,
) {
    val palette = rememberCategoriesPalette()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = RoundedCornerShape(22.dp),
            color = Color.Transparent,
            shadowElevation = 8.dp,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(iconBrush),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = iconForKey(category.iconKey),
                    contentDescription = null,
                    tint = category.accent,
                    modifier = Modifier.size(34.dp),
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category.name,
                color = palette.textPrimary,
                fontSize = 24.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = category.usageLabel(),
                color = palette.textSecondary,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CategoryTypeBadge(category = category)
                if (category.isArchived) {
                    CategoryArchivedBadge(label = archivedLabel)
                }
            }
        }

        if (category.isArchived) {
            ArchivedCategoryCheckmark(category.accent)
        }
    }
}

@Composable
private fun ArchivedCategoryCheckmark(tint: Color) {
    val palette = rememberCategoriesPalette()
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = palette.glassSurfaceSoft,
        modifier = Modifier.padding(top = 2.dp),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Done,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun CategoryCardActions(
    editLabel: String,
    deleteLabel: String,
    editTint: Color,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val palette = rememberCategoriesPalette()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryActionButton(editLabel, Icons.Rounded.Edit, editTint, onEdit, Modifier.weight(1f))
        Spacer(
            modifier = Modifier
                .width(1.dp)
                .height(24.dp)
                .background(palette.divider),
        )
        CategoryActionButton(deleteLabel, Icons.Rounded.Delete, DeleteRed, onDelete, Modifier.weight(1f))
    }
}

@Composable
internal fun CategoryTypeBadge(category: CategoryUiModel) {
    val palette = rememberCategoriesPalette()
    val isIncome = category.categoryType == CATEGORY_TYPE_INCOME
    val typeLabel = if (isIncome) {
        stringResource(Res.string.income_label)
    } else {
        stringResource(Res.string.expense_label)
    }

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = category.accent.copy(alpha = if (palette.isDark) 0.26f else 0.14f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = if (isIncome) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward,
                contentDescription = null,
                tint = category.accent,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = typeLabel,
                color = category.accent,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
internal fun CategoryArchivedBadge(label: String) {
    val palette = rememberCategoriesPalette()
    Surface(shape = RoundedCornerShape(999.dp), color = palette.glassSurfaceSoft) {
        Text(
            text = label,
            color = palette.textSecondary,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
internal fun CategoryActionButton(
    text: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(imageVector = icon, contentDescription = text, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            color = tint,
            fontSize = 18.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
