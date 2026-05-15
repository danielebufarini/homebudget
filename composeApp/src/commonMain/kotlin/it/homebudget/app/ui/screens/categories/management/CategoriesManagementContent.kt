package it.homebudget.app.ui.screens.categories.management

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.add_category
import homebudget.composeapp.generated.resources.categories
import homebudget.composeapp.generated.resources.categories_archived_badge
import homebudget.composeapp.generated.resources.categories_empty_all
import homebudget.composeapp.generated.resources.categories_empty_all_description
import homebudget.composeapp.generated.resources.categories_empty_archived
import homebudget.composeapp.generated.resources.categories_empty_archived_description
import homebudget.composeapp.generated.resources.categories_empty_expense
import homebudget.composeapp.generated.resources.categories_empty_expense_description
import homebudget.composeapp.generated.resources.categories_empty_income
import homebudget.composeapp.generated.resources.categories_empty_income_description
import homebudget.composeapp.generated.resources.categories_filter_all
import homebudget.composeapp.generated.resources.categories_filter_archived
import homebudget.composeapp.generated.resources.categories_filter_expense
import homebudget.composeapp.generated.resources.categories_filter_income
import homebudget.composeapp.generated.resources.categories_search_placeholder
import homebudget.composeapp.generated.resources.categories_sort_ascending
import homebudget.composeapp.generated.resources.categories_sort_descending
import homebudget.composeapp.generated.resources.delete
import homebudget.composeapp.generated.resources.edit_category
import homebudget.composeapp.generated.resources.expense_label
import homebudget.composeapp.generated.resources.income_label
import it.homebudget.app.database.CATEGORY_TYPE_EXPENSE
import it.homebudget.app.database.CATEGORY_TYPE_INCOME
import it.homebudget.app.ui.screens.rememberIsIosPlatform
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun CategoriesManagementContent(
    categories: List<CategoryUiModel>,
    query: String,
    selectedFilter: CategoryFilter,
    sortAscending: Boolean,
    onQueryChange: (String) -> Unit,
    onSortToggle: () -> Unit,
    onFilterChange: (CategoryFilter) -> Unit,
    onBack: (() -> Unit)?,
    onAdd: () -> Unit,
    onEdit: (CategoryUiModel) -> Unit,
    onDelete: (CategoryUiModel) -> Unit,
    modifier: Modifier = Modifier,
    externalHeaderOffset: Dp = 0.dp,
) {
    val palette = rememberCategoriesPalette()
    val isIos = rememberIsIosPlatform()
    val showInContentTitle = onBack != null
    val sortAscendingLabel = stringResource(Res.string.categories_sort_ascending)
    val sortDescendingLabel = stringResource(Res.string.categories_sort_descending)
    val density = LocalDensity.current
    val safeAreaPadding = WindowInsets.safeDrawing.asPaddingValues()
    var floatingHeaderHeightPx by remember { mutableIntStateOf(0) }
    val visibleCategories = remember(categories, query, selectedFilter, sortAscending) {
        categories
            .filter { category ->
                query.isBlank() || category.name.contains(query.trim(), ignoreCase = true)
            }
            .filter { category ->
                when (selectedFilter) {
                    CategoryFilter.All -> !category.isArchived
                    CategoryFilter.Expense ->
                        category.categoryType == CATEGORY_TYPE_EXPENSE && !category.isArchived
                    CategoryFilter.Income ->
                        category.categoryType == CATEGORY_TYPE_INCOME && !category.isArchived
                    CategoryFilter.Archived -> category.isArchived
                }
            }
            .let { filtered ->
                if (sortAscending) {
                    filtered.sortedBy { it.name.lowercase() }
                } else {
                    filtered.sortedByDescending { it.name.lowercase() }
                }
            }
    }
    val floatingHeaderHeight = with(density) {
        if (floatingHeaderHeightPx == 0) 140.dp else floatingHeaderHeightPx.toDp()
    }
    val safeTopPadding = safeAreaPadding.calculateTopPadding()
    val safeBottomPadding = safeAreaPadding.calculateBottomPadding()
    val usesExternalTopBar = externalHeaderOffset > 0.dp
    val headerTopPadding = when {
        usesExternalTopBar -> externalHeaderOffset + 10.dp
        showInContentTitle || isIos -> safeTopPadding + 10.dp
        else -> 10.dp
    }
    val listTopPadding = floatingHeaderHeight + when {
        usesExternalTopBar -> externalHeaderOffset + 18.dp
        showInContentTitle || isIos -> safeTopPadding + 18.dp
        else -> 18.dp
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 320.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = listTopPadding,
                bottom = safeBottomPadding + 128.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                CategoryFilterRow(
                    selectedFilter = selectedFilter,
                    onFilterChange = onFilterChange,
                )
            }

            if (visibleCategories.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyCategoriesCard(
                        selectedFilter = selectedFilter,
                        onAdd = onAdd,
                    )
                }
            } else {
                categoryCardItems(
                    categories = visibleCategories,
                    onEdit = onEdit,
                    onDelete = onDelete,
                )
            }
        }

        FloatingCategoriesHeader(
            query = query,
            sortAscending = sortAscending,
            sortAscendingLabel = sortAscendingLabel,
            sortDescendingLabel = sortDescendingLabel,
            onQueryChange = onQueryChange,
            onSortToggle = onSortToggle,
            onBack = onBack,
            showTitle = showInContentTitle,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(start = 20.dp, end = 20.dp, top = headerTopPadding)
                .onGloballyPositioned { coordinates ->
                    floatingHeaderHeightPx = coordinates.size.height
                },
        )

    }
}

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
    val categoriesTitle = stringResource(Res.string.categories)
    val shape = RoundedCornerShape(if (isIos) 34.dp else 30.dp)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(
                border = BorderStroke(
                    1.dp,
                    if (palette.isDark) {
                        Color.White.copy(alpha = 0.14f)
                    } else {
                        Color.White.copy(alpha = 0.52f)
                    },
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
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = searchPlaceholder,
                        color = palette.textMuted.copy(alpha = 0.72f),
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions.Default,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = palette.textPrimary,
                    unfocusedTextColor = palette.textPrimary,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
            )
            IconButton(
                onClick = onSortToggle,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = if (sortAscending) {
                        Icons.Rounded.ArrowUpward
                    } else {
                        Icons.Rounded.ArrowDownward
                    },
                    contentDescription = if (sortAscending) {
                        sortAscendingLabel
                    } else {
                        sortDescendingLabel
                    },
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
    val palette = rememberCategoriesPalette()
    val filterAllLabel = stringResource(Res.string.categories_filter_all)
    val filterExpenseLabel = stringResource(Res.string.categories_filter_expense)
    val filterIncomeLabel = stringResource(Res.string.categories_filter_income)
    val filterArchivedLabel = stringResource(Res.string.categories_filter_archived)
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CategoryFilter.entries.forEach { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterChange(filter) },
                label = {
                    Text(
                        when (filter) {
                            CategoryFilter.All -> filterAllLabel
                            CategoryFilter.Expense -> filterExpenseLabel
                            CategoryFilter.Income -> filterIncomeLabel
                            CategoryFilter.Archived -> filterArchivedLabel
                        },
                    )
                },
                leadingIcon = {
                    when (filter) {
                        CategoryFilter.All -> Unit
                        CategoryFilter.Expense -> Icon(Icons.Rounded.ArrowUpward, null, Modifier.size(16.dp))
                        CategoryFilter.Income -> Icon(Icons.Rounded.ArrowDownward, null, Modifier.size(16.dp))
                        CategoryFilter.Archived -> Icon(Icons.Rounded.AccountBalanceWallet, null, Modifier.size(16.dp))
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AccentPurple,
                    selectedLabelColor = Color.White,
                    selectedLeadingIconColor = Color.White,
                    containerColor = palette.glassSurfaceSoft,
                    labelColor = palette.textSecondary,
                    iconColor = palette.textSecondary,
                ),
            )
        }
    }
}

internal fun LazyGridScope.categoryCardItems(
    categories: List<CategoryUiModel>,
    onEdit: (CategoryUiModel) -> Unit,
    onDelete: (CategoryUiModel) -> Unit,
) {
    items(
        items = categories,
        key = { it.id },
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
    val usageLabel = category.usageLabel()
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
                        text = usageLabel,
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
                                tint = category.accent,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }

            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .padding(horizontal = 20.dp)
                    .background(palette.divider),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryActionButton(
                    text = editLabel,
                    icon = Icons.Rounded.Edit,
                    tint = if (category.isArchived) palette.textSecondary else category.accent,
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                )

                Spacer(
                    modifier = Modifier
                        .width(1.dp)
                        .height(24.dp)
                        .background(palette.divider),
                )

                CategoryActionButton(
                    text = deleteLabel,
                    icon = Icons.Rounded.Delete,
                    tint = DeleteRed,
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
internal fun CategoryTypeBadge(
    category: CategoryUiModel,
) {
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
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = palette.glassSurfaceSoft,
    ) {
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
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
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

@Composable
internal fun EmptyCategoriesCard(
    selectedFilter: CategoryFilter,
    onAdd: () -> Unit,
) {
    val palette = rememberCategoriesPalette()
    val noCategoriesYet = stringResource(Res.string.categories_empty_all)
    val noExpenseCategories = stringResource(Res.string.categories_empty_expense)
    val noIncomeCategories = stringResource(Res.string.categories_empty_income)
    val noArchivedCategories = stringResource(Res.string.categories_empty_archived)
    val allCategoriesDescription = stringResource(Res.string.categories_empty_all_description)
    val expenseCategoriesDescription = stringResource(Res.string.categories_empty_expense_description)
    val incomeCategoriesDescription = stringResource(Res.string.categories_empty_income_description)
    val archivedCategoriesDescription = stringResource(Res.string.categories_empty_archived_description)
    val addCategoryLabel = stringResource(Res.string.add_category)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp),
        shape = RoundedCornerShape(32.dp),
        color = palette.glassSurface,
        tonalElevation = 6.dp,
        shadowElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                modifier = Modifier.size(78.dp),
                shape = RoundedCornerShape(28.dp),
                color = AccentPurple.copy(alpha = 0.12f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Category,
                        contentDescription = null,
                        tint = AccentPurple,
                        modifier = Modifier.size(38.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = when (selectedFilter) {
                    CategoryFilter.All -> noCategoriesYet
                    CategoryFilter.Expense -> noExpenseCategories
                    CategoryFilter.Income -> noIncomeCategories
                    CategoryFilter.Archived -> noArchivedCategories
                },
                color = palette.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 21.sp,
            )
            Text(
                text = when (selectedFilter) {
                    CategoryFilter.All -> allCategoriesDescription
                    CategoryFilter.Expense -> expenseCategoriesDescription
                    CategoryFilter.Income -> incomeCategoriesDescription
                    CategoryFilter.Archived -> archivedCategoriesDescription
                },
                color = palette.textSecondary,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 6.dp),
            )

            Spacer(Modifier.height(18.dp))

            Button(
                onClick = onAdd,
                colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(addCategoryLabel)
            }
        }
    }
}
