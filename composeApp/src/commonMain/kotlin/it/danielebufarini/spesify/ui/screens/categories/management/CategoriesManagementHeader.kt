package it.danielebufarini.spesify.ui.screens.categories.management

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.danielebufarini.spesify.ui.screens.platform.rememberIsIosPlatform
import org.jetbrains.compose.resources.stringResource
import spesify.composeapp.generated.resources.Res
import spesify.composeapp.generated.resources.categories
import spesify.composeapp.generated.resources.categories_filter_all
import spesify.composeapp.generated.resources.categories_filter_archived
import spesify.composeapp.generated.resources.categories_filter_expense
import spesify.composeapp.generated.resources.categories_filter_income
import spesify.composeapp.generated.resources.categories_search_placeholder

@Composable
internal fun FloatingCategoriesHeader(
    query: String,
    sortAscending: Boolean,
    sortAscendingLabel: String,
    sortDescendingLabel: String,
    onQueryChange: (String) -> Unit,
    onSortToggle: () -> Unit,
    onBack: (() -> Unit)?,
    showTitle: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = rememberCategoriesPalette()
    val isIos = rememberIsIosPlatform()
    val shape = RoundedCornerShape(if (isIos) 34.dp else 30.dp)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(
                border = BorderStroke(
                    1.dp,
                    if (palette.isDark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.52f),
                ),
                shape = shape,
            ),
        shape = shape,
        color = palette.glassStrong,
        tonalElevation = 14.dp,
        shadowElevation = if (isIos) 18.dp else 12.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (showTitle) 12.dp else 0.dp),
        ) {
            if (showTitle) {
                CategoriesHeaderTitle(onBack = onBack)
            }
            CategorySearchBar(
                query = query,
                sortAscending = sortAscending,
                sortAscendingLabel = sortAscendingLabel,
                sortDescendingLabel = sortDescendingLabel,
                onQueryChange = onQueryChange,
                onSortToggle = onSortToggle,
            )
        }
    }
}

@Composable
private fun CategoriesHeaderTitle(onBack: (() -> Unit)?) {
    val palette = rememberCategoriesPalette()
    val isIos = rememberIsIosPlatform()
    val categoriesTitle = stringResource(Res.string.categories)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (onBack != null) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(16.dp),
                color = palette.iconSurface,
                tonalElevation = 6.dp,
                shadowElevation = 2.dp,
                onClick = onBack,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = null,
                        tint = AccentPurple,
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
        }

        Text(
            text = categoriesTitle,
            color = palette.textPrimary,
            fontSize = if (isIos) 27.sp else 30.sp,
            lineHeight = if (isIos) 30.sp else 33.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun CategorySearchBar(
    query: String,
    sortAscending: Boolean,
    sortAscendingLabel: String,
    sortDescendingLabel: String,
    onQueryChange: (String) -> Unit,
    onSortToggle: () -> Unit,
) {
    val palette = rememberCategoriesPalette()
    val searchPlaceholder = stringResource(Res.string.categories_search_placeholder)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = palette.glassSurfaceSoft,
        tonalElevation = 8.dp,
        shadowElevation = 2.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = palette.textMuted,
                modifier = Modifier.size(22.dp),
            )
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
                singleLine = true,
                textStyle = TextStyle(
                    color = palette.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                ),
                cursorBrush = SolidColor(AccentPurple),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions.Default,
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (query.isBlank()) {
                            Text(
                                text = searchPlaceholder,
                                color = palette.textMuted.copy(alpha = 0.72f),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            IconButton(onClick = onSortToggle, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = if (sortAscending) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                    contentDescription = if (sortAscending) sortAscendingLabel else sortDescendingLabel,
                    tint = palette.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
internal fun CategoryFilterRow(
    selectedFilter: CategoryFilter,
    onFilterChange: (CategoryFilter) -> Unit,
) {
    val filterAllLabel = stringResource(Res.string.categories_filter_all)
    val filterExpenseLabel = stringResource(Res.string.categories_filter_expense)
    val filterIncomeLabel = stringResource(Res.string.categories_filter_income)
    val filterArchivedLabel = stringResource(Res.string.categories_filter_archived)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CategoryFilter.entries.forEach { filter ->
            CompactCategoryFilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterChange(filter) },
                label = when (filter) {
                    CategoryFilter.All -> filterAllLabel
                    CategoryFilter.Expense -> filterExpenseLabel
                    CategoryFilter.Income -> filterIncomeLabel
                    CategoryFilter.Archived -> filterArchivedLabel
                },
                icon = when (filter) {
                    CategoryFilter.All -> null
                    CategoryFilter.Expense -> Icons.Rounded.ArrowUpward
                    CategoryFilter.Income -> Icons.Rounded.ArrowDownward
                    CategoryFilter.Archived -> Icons.Rounded.AccountBalanceWallet
                },
            )
        }
    }
}

@Composable
private fun CompactCategoryFilterChip(
    selected: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    onClick: () -> Unit,
) {
    val palette = rememberCategoriesPalette()
    val contentColor = if (selected) Color.White else palette.textSecondary

    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) AccentPurple else palette.glassSurfaceSoft,
        border = if (selected) {
            null
        } else {
            BorderStroke(1.dp, palette.cardBorder.copy(alpha = 0.72f))
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = label,
                color = contentColor,
                fontSize = 14.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}
