package it.danielebufarini.homebudget.ui.screens.categories.management

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.categories_sort_ascending
import homebudget.composeapp.generated.resources.categories_sort_descending
import it.danielebufarini.homebudget.database.CATEGORY_TYPE_EXPENSE
import it.danielebufarini.homebudget.database.CATEGORY_TYPE_INCOME
import it.danielebufarini.homebudget.ui.screens.platform.rememberIsIosPlatform
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
        categories.visibleFor(query, selectedFilter, sortAscending)
    }
    val floatingHeaderHeight = with(density) {
        if (floatingHeaderHeightPx == 0) 140.dp else floatingHeaderHeightPx.toDp()
    }
    val safeTopPadding = safeAreaPadding.calculateTopPadding()
    val safeBottomPadding = safeAreaPadding.calculateBottomPadding()
    val usesExternalTopBar = externalHeaderOffset > 0.dp
    val hostedIosHeaderOffset = if (isIos && onBack == null) 92.dp else 0.dp
    val headerTopPadding = when {
        usesExternalTopBar -> externalHeaderOffset + 10.dp
        showInContentTitle || isIos -> safeTopPadding + hostedIosHeaderOffset + 10.dp
        else -> 10.dp
    }
    val listTopPadding = floatingHeaderHeight + when {
        usesExternalTopBar -> externalHeaderOffset + 18.dp
        showInContentTitle || isIos -> safeTopPadding + hostedIosHeaderOffset + 18.dp
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

private fun List<CategoryUiModel>.visibleFor(
    query: String,
    selectedFilter: CategoryFilter,
    sortAscending: Boolean,
): List<CategoryUiModel> {
    val normalizedQuery = query.trim()
    val filtered = filter { category ->
        (normalizedQuery.isBlank() || category.name.contains(normalizedQuery, ignoreCase = true)) &&
            category.matches(selectedFilter)
    }
    return if (sortAscending) {
        filtered.sortedBy { it.name.lowercase() }
    } else {
        filtered.sortedByDescending { it.name.lowercase() }
    }
}

private fun CategoryUiModel.matches(filter: CategoryFilter) = when (filter) {
    CategoryFilter.All -> !isArchived
    CategoryFilter.Expense -> categoryType == CATEGORY_TYPE_EXPENSE && !isArchived
    CategoryFilter.Income -> categoryType == CATEGORY_TYPE_INCOME && !isArchived
    CategoryFilter.Archived -> isArchived
}
