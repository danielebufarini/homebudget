package it.danielebufarini.homebudget.ui.screens.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.add_category
import homebudget.composeapp.generated.resources.select_category
import it.danielebufarini.homebudget.database.Category
import it.danielebufarini.homebudget.ui.screens.categories.CategoryIcon
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoryPickerSheet(
    categories: List<Category>,
    selectedCategoryId: String,
    resolveCategoryName: (Category) -> String,
    onDismiss: () -> Unit,
    onAddCategory: () -> Unit,
    onCategorySelected: (String) -> Unit,
) {
    val selectCategoryLabel = stringResource(Res.string.select_category)
    val addCategoryLabel = stringResource(Res.string.add_category)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = selectCategoryLabel,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            CategoryGridSection(
                title = selectCategoryLabel,
                categories = categories,
                selectedCategoryId = selectedCategoryId,
                resolveCategoryName = resolveCategoryName,
                showAddTile = true,
                addCategoryLabel = addCategoryLabel,
                onAddCategory = onAddCategory,
                onCategorySelected = { category -> onCategorySelected(category.id) },
            )
        }
    }
}

@Composable
private fun CategoryGridSection(
    title: String,
    categories: List<Category>,
    selectedCategoryId: String,
    resolveCategoryName: (Category) -> String,
    showAddTile: Boolean,
    addCategoryLabel: String,
    onAddCategory: () -> Unit,
    onCategorySelected: (Category) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        CategoryGrid(
            categories = categories,
            selectedCategoryId = selectedCategoryId,
            resolveCategoryName = resolveCategoryName,
            showAddTile = showAddTile,
            addCategoryLabel = addCategoryLabel,
            onAddCategory = onAddCategory,
            onCategorySelected = onCategorySelected,
        )
    }
}

@Composable
private fun CategoryGrid(
    categories: List<Category>,
    selectedCategoryId: String,
    resolveCategoryName: (Category) -> String,
    showAddTile: Boolean,
    addCategoryLabel: String,
    onAddCategory: () -> Unit,
    onCategorySelected: (Category) -> Unit,
) {
    val columns = 4
    val entries = if (showAddTile) listOf<Category?>(null) + categories else categories

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        entries.chunked(columns).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowItems.forEach { category ->
                    if (category == null) {
                        AddCategoryGridTile(
                            label = addCategoryLabel,
                            onClick = onAddCategory,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        CategoryGridTile(
                            category = category,
                            categoryName = resolveCategoryName(category),
                            isSelected = category.id == selectedCategoryId,
                            onClick = { onCategorySelected(category) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                repeat(columns - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AddCategoryGridTile(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .aspectRatio(0.90f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = label,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = label,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 11.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CategoryGridTile(
    category: Category,
    categoryName: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    val tileBorderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Box(
        modifier = modifier
            .aspectRatio(0.90f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
            .border(if (isSelected) 2.dp else 1.dp, tileBorderColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CategoryIcon(
                iconKey = category.icon,
                colorKey = category.id,
                modifier = Modifier.size(24.dp),
                tint = null,
            )
            Text(
                text = categoryName,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp, lineHeight = 11.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                textAlign = TextAlign.Center,
            )
        }

        if (isSelected) {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd),
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(4.dp)
                        .size(14.dp),
                )
            }
        }
    }
}
