package it.homebudget.app.ui.screens.categories

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.data.IdGenerator
import it.homebudget.app.database.CATEGORY_TYPE_EXPENSE
import it.homebudget.app.database.CATEGORY_TYPE_INCOME
import it.homebudget.app.database.Category
import it.homebudget.app.database.DEFAULT_CATEGORY_COLOR
import it.homebudget.app.database.Expense
import it.homebudget.app.database.Income
import it.homebudget.app.ui.screens.rememberIsIosPlatform
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject
import kotlin.random.Random
import kotlin.time.Clock

object BentoCategoriesScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        BentoCategoriesRoute(
            onBack = { navigator?.pop() }
        )
    }
}

@Composable
fun BentoCategoriesRoute(
    repository: ExpenseRepository = koinInject(),
    onBack: (() -> Unit)? = null,
) {
    LaunchedEffect(repository) {
        repository.insertDefaultCategoriesIfEmpty()
    }

    val categories by repository.getAllCategories().collectAsState(initial = emptyList())
    val expenses by repository.getAllExpenses().collectAsState(initial = emptyList())
    val incomes by repository.getAllIncomes().collectAsState(initial = emptyList())

    val monthBounds = remember { currentMonthBoundsMillis() }
    val customCategories = remember(categories, expenses, incomes, monthBounds) {
        buildCategoryUiModels(
            categories = categories,
            expenses = expenses,
            incomes = incomes,
            monthStartMillis = monthBounds.first,
            nextMonthStartMillis = monthBounds.second,
        )
    }

    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(CategoryFilter.All.name) }
    var editorTarget by remember { mutableStateOf<CategoryUiModel?>(null) }
    var deleteTarget by remember { mutableStateOf<CategoryUiModel?>(null) }

    val scope = rememberCoroutineScope()

    BentoCategoriesScreenContent(
        categories = customCategories,
        query = query,
        selectedFilter = CategoryFilter.valueOf(filter),
        onQueryChange = { query = it },
        onFilterChange = { filter = it.name },
        onBack = onBack,
        onAdd = { editorTarget = CategoryUiModel.newEmpty() },
        onEdit = { category -> editorTarget = category },
        onDelete = { category -> deleteTarget = category },
    )

    editorTarget?.let { target ->
        CategoryEditorSheet(
            category = target,
            onDismiss = { editorTarget = null },
            onSave = { edited ->
                scope.launch {
                    if (edited.id.isBlank()) {
                        repository.insertCategory(
                            id = buildCustomCategoryId(),
                            name = edited.name.trim(),
                            icon = edited.iconKey,
                            isCustom = true,
                            color = edited.colorHex,
                            categoryType = edited.categoryType,
                            isArchived = false
                        )
                    } else {
                        repository.updateCategory(
                            id = edited.id,
                            name = edited.name.trim(),
                            icon = edited.iconKey,
                            color = edited.colorHex,
                            categoryType = edited.categoryType
                        )
                    }
                    editorTarget = null
                }
            },
            onDelete = {
                if (target.id.isNotBlank()) {
                    deleteTarget = target
                    editorTarget = null
                }
            }
        )
    }

    deleteTarget?.let { target ->
        DeleteCategoryDialog(
            category = target,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                scope.launch {
                    repository.deleteCategory(target.id)
                    deleteTarget = null
                }
            }
        )
    }
}

@Composable
private fun BentoCategoriesScreenContent(
    categories: List<CategoryUiModel>,
    query: String,
    selectedFilter: CategoryFilter,
    onQueryChange: (String) -> Unit,
    onFilterChange: (CategoryFilter) -> Unit,
    onBack: (() -> Unit)?,
    onAdd: () -> Unit,
    onEdit: (CategoryUiModel) -> Unit,
    onDelete: (CategoryUiModel) -> Unit,
) {
    val palette = rememberBentoPalette()
    val visibleCategories = remember(categories, query, selectedFilter) {
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
            .sortedWith(
                compareByDescending<CategoryUiModel> { it.transactionCount }
                    .thenBy { it.name.lowercase() }
            )
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                containerColor = AccentPurple,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 12.dp),
                shape = CircleShape,
                modifier = Modifier.size(72.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Add category",
                    modifier = Modifier.size(34.dp),
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.background)
                .padding(padding)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 10.dp,
                    bottom = 128.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    BentoCategoriesHeader(
                        onBack = onBack,
                    )
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    SearchAndFilters(
                        query = query,
                        selectedFilter = selectedFilter,
                        onQueryChange = onQueryChange,
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
                    bentoCategoryItems(
                        categories = visibleCategories,
                        onEdit = onEdit,
                        onDelete = onDelete,
                    )
                }
            }
        }
    }
}

@Composable
private fun BentoCategoriesHeader(
    onBack: (() -> Unit)?,
) {
    val palette = rememberBentoPalette()
    val isIos = rememberIsIosPlatform()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp),
        shape = RoundedCornerShape(if (isIos) 34.dp else 30.dp),
        color = palette.glassStrong,
        tonalElevation = 10.dp,
        shadowElevation = if (isIos) 14.dp else 9.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(18.dp),
                color = palette.iconSurface,
                tonalElevation = 6.dp,
                shadowElevation = 2.dp,
                onClick = { onBack?.invoke() },
                enabled = onBack != null,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = null,
                        tint = AccentPurple,
                    )
                }
            }

            Spacer(Modifier.width(18.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Custom Categories",
                    color = palette.textPrimary,
                    fontSize = if (isIos) 28.sp else 32.sp,
                    lineHeight = if (isIos) 31.sp else 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Organize your spending your way",
                    color = palette.textSecondary,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SearchAndFilters(
    query: String,
    selectedFilter: CategoryFilter,
    onQueryChange: (String) -> Unit,
    onFilterChange: (CategoryFilter) -> Unit,
) {
    val palette = rememberBentoPalette()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = palette.glassSurface,
            tonalElevation = 8.dp,
            shadowElevation = 3.dp,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = palette.textMuted,
                    modifier = Modifier.size(26.dp),
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            text = "Search categories...",
                            color = palette.textMuted.copy(alpha = 0.72f)
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
                    )
                )
                Icon(
                    imageVector = Icons.Rounded.FilterList,
                    contentDescription = "Filters",
                    tint = palette.textSecondary,
                    modifier = Modifier.size(26.dp),
                )
            }
        }

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CategoryFilter.entries.forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { onFilterChange(filter) },
                    label = { Text(filter.label) },
                    leadingIcon = {
                        when (filter) {
                            CategoryFilter.All -> null
                            CategoryFilter.Expense -> Icon(Icons.Rounded.ArrowUpward, null, Modifier.size(16.dp))
                            CategoryFilter.Income -> Icon(Icons.Rounded.ArrowDownward, null, Modifier.size(16.dp))
                            CategoryFilter.Archived -> Icon(Icons.Rounded.AccountBalanceWallet, null, Modifier.size(16.dp))
                        }
                    },
                    colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
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
}

private fun LazyGridScope.bentoCategoryItems(
    categories: List<CategoryUiModel>,
    onEdit: (CategoryUiModel) -> Unit,
    onDelete: (CategoryUiModel) -> Unit,
) {
    itemsIndexed(
        items = categories,
        key = { _, item -> item.id },
        span = { index, _ ->
            GridItemSpan(if (index.isWideBentoCard()) 2 else 1)
        }
    ) { index, category ->
        BentoCategoryCard(
            category = category,
            isWide = index.isWideBentoCard(),
            onEdit = { onEdit(category) },
            onDelete = { onDelete(category) },
        )
    }
}

@Composable
private fun BentoCategoryCard(
    category: CategoryUiModel,
    isWide: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val palette = rememberBentoPalette()
    val gradient = remember(category.accent) {
        Brush.linearGradient(
            colors = listOf(
                category.accent.copy(alpha = 0.17f),
                category.accent.copy(alpha = 0.07f),
                palette.cardHighlight,
            )
        )
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isWide) 172.dp else 226.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = palette.cardSurface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 5.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradient)
                .padding(16.dp)
        ) {
            if (isWide) {
                WideCategoryCardContent(
                    category = category,
                    onEdit = onEdit,
                    onDelete = onDelete,
                )
            } else {
                TallCategoryCardContent(
                    category = category,
                    onEdit = onEdit,
                    onDelete = onDelete,
                )
            }
        }
    }
}

@Composable
private fun TallCategoryCardContent(
    category: CategoryUiModel,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val palette = rememberBentoPalette()
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.Top) {
            CategoryIconBubble(category)
            Spacer(Modifier.weight(1f))
            SmallRoundAction(
                icon = Icons.Rounded.Edit,
                tint = category.accent,
                contentDescription = "Edit ${category.name}",
                onClick = onEdit,
            )
            Spacer(Modifier.width(8.dp))
            SmallRoundAction(
                icon = Icons.Rounded.Delete,
                tint = DeleteRed,
                contentDescription = "Delete ${category.name}",
                onClick = onDelete,
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = category.name,
            color = palette.textPrimary,
            fontSize = 21.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${category.transactionCount} transactions",
            color = palette.textSecondary,
            fontSize = 15.sp,
            maxLines = 1,
        )

        Spacer(Modifier.weight(1f))

        CategoryAmountPanel(
            category = category,
            compact = true
        )
    }
}

@Composable
private fun WideCategoryCardContent(
    category: CategoryUiModel,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val palette = rememberBentoPalette()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxSize(),
    ) {
        CategoryIconBubble(category, size = 72.dp)

        Spacer(Modifier.width(18.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category.name,
                color = palette.textPrimary,
                fontSize = 23.sp,
                lineHeight = 25.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${category.transactionCount} transactions",
                color = palette.textSecondary,
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(16.dp))
            CategoryAmountPanel(
                category = category,
                compact = false
            )
        }

        Spacer(Modifier.width(10.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallRoundAction(
                icon = Icons.Rounded.Edit,
                tint = category.accent,
                contentDescription = "Edit ${category.name}",
                onClick = onEdit,
            )
            SmallRoundAction(
                icon = Icons.Rounded.Delete,
                tint = DeleteRed,
                contentDescription = "Delete ${category.name}",
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun CategoryAmountPanel(
    category: CategoryUiModel,
    compact: Boolean,
) {
    val palette = rememberBentoPalette()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = palette.panelSurface,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 14.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .width(if (compact) 74.dp else 96.dp)
            ) {
                Text(
                    text = "This month",
                    color = palette.textSecondary,
                    fontSize = if (compact) 8.sp else 10.sp,
                    lineHeight = if (compact) 10.sp else 12.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
                Text(
                    text = category.monthAmount.formatMinorUnits(),
                    color = category.accent,
                    fontSize = if (compact) 14.sp else 18.sp,
                    lineHeight = if (compact) 17.sp else 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Sparkline(
                color = category.accent,
                modifier = Modifier
                    .width(if (compact) 58.dp else 86.dp)
                    .height(if (compact) 38.dp else 42.dp),
                seed = category.name.hashCode(),
            )
        }
    }
}

@Composable
private fun CategoryIconBubble(
    category: CategoryUiModel,
    size: androidx.compose.ui.unit.Dp = 66.dp,
) {
    val palette = rememberBentoPalette()
    Surface(
        modifier = Modifier.size(size),
        shape = RoundedCornerShape(23.dp),
        color = palette.iconSurface,
        shadowElevation = 5.dp,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.background(
                Brush.radialGradient(
                    listOf(
                        category.accent.copy(alpha = 0.35f),
                        category.accent.copy(alpha = 0.05f),
                        Color.Transparent,
                    )
                )
            )
        ) {
            Icon(
                imageVector = iconForKey(category.iconKey),
                contentDescription = null,
                tint = category.accent,
                modifier = Modifier.size(if (size > 70.dp) 36.dp else 31.dp),
            )
        }
    }
}

@Composable
private fun SmallRoundAction(
    icon: ImageVector,
    tint: Color,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val palette = rememberBentoPalette()
    Surface(
        modifier = Modifier.size(38.dp),
        shape = CircleShape,
        color = palette.iconSurface,
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable
private fun Sparkline(
    color: Color,
    modifier: Modifier = Modifier,
    seed: Int,
) {
    val values = remember(seed) {
        val random = Random(seed)
        List(7) { random.nextFloat().coerceIn(0.08f, 0.95f) }
    }

    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas

        val widthStep = size.width / (values.lastIndex)
        val path = Path()

        values.forEachIndexed { index, value ->
            val x = widthStep * index
            val y = size.height - (value * size.height)
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = color,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 4.dp.toPx(),
                cap = StrokeCap.Round,
            )
        )

        val lastY = size.height - (values.last() * size.height)
        drawCircle(
            color = color,
            radius = 4.5.dp.toPx(),
            center = Offset(size.width, lastY),
        )
    }
}

@Composable
private fun EmptyCategoriesCard(
    selectedFilter: CategoryFilter,
    onAdd: () -> Unit,
) {
    val palette = rememberBentoPalette()
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
                    CategoryFilter.All,
                    CategoryFilter.Expense -> "No custom categories yet"
                    CategoryFilter.Income -> "No income categories yet"
                    CategoryFilter.Archived -> "No archived categories"
                },
                color = palette.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 21.sp,
            )
            Text(
                text = when (selectedFilter) {
                    CategoryFilter.All,
                    CategoryFilter.Expense -> "Create your first custom category to personalize your budget."
                    CategoryFilter.Income -> "Income categories can be assigned to revenue entries and managed here."
                    CategoryFilter.Archived -> "Archived categories stay attached to old transactions but disappear from future pickers."
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
                Text("Add category")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CategoryEditorSheet(
    category: CategoryUiModel,
    onDismiss: () -> Unit,
    onSave: (CategoryUiModel) -> Unit,
    onDelete: () -> Unit,
) {
    val palette = rememberBentoPalette()
    var name by rememberSaveable(category.id) { mutableStateOf(category.name) }
    var iconKey by rememberSaveable(category.id) { mutableStateOf(category.iconKey.ifBlank { "category" }) }
    var categoryType by rememberSaveable(category.id) { mutableStateOf(category.categoryType) }
    var selectedColorIndex by rememberSaveable(category.id) {
        mutableIntStateOf(
            CategoryAccentPaletteHex.indexOf(category.colorHex)
                .takeIf { it >= 0 } ?: 0
        )
    }

    val canSave = name.trim().isNotBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = palette.sheetBackground,
        contentColor = palette.textPrimary,
        shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (category.id.isBlank()) "New Category" else "Edit Category",
                    color = palette.textPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = palette.textPrimary,
                    )
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 24) name = it },
                label = { Text("Category name") },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = iconForKey(iconKey),
                        contentDescription = null,
                        tint = CategoryAccentPalette[selectedColorIndex],
                    )
                },
                trailingIcon = {
                    Text(
                        text = "${name.length} / 24",
                        color = palette.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CategoryAccentPalette[selectedColorIndex],
                    unfocusedBorderColor = palette.textMuted.copy(alpha = 0.35f),
                    focusedTextColor = palette.textPrimary,
                    unfocusedTextColor = palette.textPrimary,
                    focusedLabelColor = palette.textSecondary,
                    unfocusedLabelColor = palette.textMuted,
                    focusedContainerColor = palette.glassSurfaceSoft,
                    unfocusedContainerColor = palette.glassSurfaceSoft,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Choose an icon",
                    color = palette.textSecondary,
                    fontWeight = FontWeight.SemiBold,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    EditorIconOptions.forEach { option ->
                        val selected = iconKey == option.key
                        Surface(
                            modifier = Modifier
                                .size(54.dp)
                                .clickable { iconKey = option.key },
                            shape = RoundedCornerShape(18.dp),
                            color = if (selected) {
                                CategoryAccentPalette[selectedColorIndex]
                            } else {
                                palette.glassSurfaceSoft
                            },
                            tonalElevation = if (selected) 8.dp else 0.dp,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = option.icon,
                                    contentDescription = option.key,
                                    tint = if (selected) Color.White else palette.textSecondary,
                                )
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Choose a color",
                    color = palette.textSecondary,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    CategoryAccentPalette.forEachIndexed { index, color ->
                        val selected = selectedColorIndex == index
                        Surface(
                            modifier = Modifier
                                .size(42.dp)
                                .clickable { selectedColorIndex = index },
                            shape = CircleShape,
                            color = color,
                            border = if (selected) androidx.compose.foundation.BorderStroke(
                                width = 3.dp,
                                color = Color.White.copy(alpha = 0.9f),
                            ) else null,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Done,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = palette.glassSurfaceSoft,
            ) {
                Row(
                    modifier = Modifier.padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                        AssistChip(
                            onClick = { categoryType = CATEGORY_TYPE_EXPENSE },
                            label = { Text("Expense") },
                            leadingIcon = { Icon(Icons.Rounded.ArrowUpward, null, Modifier.size(18.dp)) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (categoryType == CATEGORY_TYPE_EXPENSE) {
                                    CategoryAccentPalette[selectedColorIndex].copy(alpha = 0.18f)
                                } else {
                                    Color.White.copy(alpha = 0.04f)
                                },
                                labelColor = if (categoryType == CATEGORY_TYPE_EXPENSE) {
                                    palette.textPrimary
                                } else {
                                    palette.textMuted
                                },
                                leadingIconContentColor = if (categoryType == CATEGORY_TYPE_EXPENSE) {
                                    CategoryAccentPalette[selectedColorIndex]
                                } else {
                                    palette.textMuted
                                },
                            ),
                            border = null,
                            modifier = Modifier.weight(1f),
                        )
                        AssistChip(
                            onClick = { categoryType = CATEGORY_TYPE_INCOME },
                            label = { Text("Income") },
                            leadingIcon = { Icon(Icons.Rounded.ArrowDownward, null, Modifier.size(18.dp)) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (categoryType == CATEGORY_TYPE_INCOME) {
                                    CategoryAccentPalette[selectedColorIndex].copy(alpha = 0.18f)
                                } else {
                                    Color.White.copy(alpha = 0.04f)
                                },
                                labelColor = if (categoryType == CATEGORY_TYPE_INCOME) {
                                    palette.textPrimary
                                } else {
                                    palette.textMuted
                                },
                                leadingIconContentColor = if (categoryType == CATEGORY_TYPE_INCOME) {
                                    CategoryAccentPalette[selectedColorIndex]
                                } else {
                                    palette.textMuted
                                },
                            ),
                            border = null,
                            modifier = Modifier.weight(1f),
                        )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = DeleteRed.copy(alpha = 0.12f),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = null,
                        tint = DeleteRed,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Delete this category?",
                            color = DeleteRed,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Past transactions are kept, but the category will disappear from future selection.",
                            color = palette.textSecondary,
                            fontSize = 13.sp,
                        )
                    }
                    if (category.id.isNotBlank()) {
                        TextButton(onClick = onDelete) {
                            Text("Delete", color = DeleteRed)
                        }
                    }
                }
            }

            Button(
                onClick = {
                    onSave(
                        category.copy(
                            name = name.trim(),
                            iconKey = iconKey,
                            accent = CategoryAccentPalette[selectedColorIndex],
                            colorHex = CategoryAccentPaletteHex[selectedColorIndex],
                            categoryType = categoryType,
                        )
                    )
                },
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CategoryAccentPalette[selectedColorIndex],
                    contentColor = Color.White,
                ),
            ) {
                Text(
                    text = "Save Changes",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun DeleteCategoryDialog(
    category: CategoryUiModel,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val palette = rememberBentoPalette()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.sheetBackground,
        textContentColor = palette.textSecondary,
        titleContentColor = palette.textPrimary,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = null,
                tint = DeleteRed,
            )
        },
        title = { Text("Delete ${category.name}?") },
        text = {
            Text(
                "This archives the custom category. Existing transactions keep it, but it disappears from future selections."
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = DeleteRed),
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Immutable
private data class CategoryUiModel(
    val id: String,
    val name: String,
    val iconKey: String,
    val isCustom: Boolean,
    val colorHex: String,
    val categoryType: String,
    val isArchived: Boolean,
    val transactionCount: Int,
    val monthAmount: Long,
    val accent: Color,
) {
    companion object {
        fun newEmpty(): CategoryUiModel =
            CategoryUiModel(
                id = "",
                name = "",
                iconKey = "category",
                isCustom = true,
                colorHex = DEFAULT_CATEGORY_COLOR,
                categoryType = CATEGORY_TYPE_EXPENSE,
                isArchived = false,
                transactionCount = 0,
                monthAmount = 0L,
                accent = CategoryAccentPalette.first(),
            )
    }
}

private enum class CategoryFilter(val label: String) {
    All("All"),
    Expense("Expense"),
    Income("Income"),
    Archived("Archived"),
}

@Immutable
private data class EditorIconOption(
    val key: String,
    val icon: ImageVector,
)

private val EditorIconOptions = listOf(
    EditorIconOption("shopping_cart", Icons.Rounded.ShoppingCart),
    EditorIconOption("restaurant", Icons.Rounded.Restaurant),
    EditorIconOption("flight", Icons.Rounded.Flight),
    EditorIconOption("pets", Icons.Rounded.Pets),
    EditorIconOption("local_hospital", Icons.Rounded.LocalHospital),
    EditorIconOption("home", Icons.Rounded.Home),
    EditorIconOption("directions_car", Icons.Rounded.DirectionsCar),
    EditorIconOption("work", Icons.Rounded.Work),
    EditorIconOption("coffee", Icons.Rounded.Coffee),
    EditorIconOption("savings", Icons.Rounded.Savings),
    EditorIconOption("person", Icons.Rounded.Person),
    EditorIconOption("category", Icons.Rounded.Category),
)

private fun buildCategoryUiModels(
    categories: List<Category>,
    expenses: List<Expense>,
    incomes: List<Income>,
    monthStartMillis: Long,
    nextMonthStartMillis: Long,
): List<CategoryUiModel> {
    val expensesByCategory = expenses.groupBy { it.categoryId }
    val incomesByCategory = incomes
        .filter { !it.categoryId.isNullOrBlank() }
        .groupBy { it.categoryId.orEmpty() }
    val monthExpensesByCategory = expenses
        .asSequence()
        .filter { it.date >= monthStartMillis && it.date < nextMonthStartMillis }
        .groupBy { it.categoryId }
    val monthIncomesByCategory = incomes
        .asSequence()
        .filter { !it.categoryId.isNullOrBlank() }
        .filter { it.date >= monthStartMillis && it.date < nextMonthStartMillis }
        .groupBy { it.categoryId.orEmpty() }

    return categories
        .filter { it.isCustom == 1L }
        .map { category ->
            val allExpenses = expensesByCategory[category.id].orEmpty()
            val allIncomes = incomesByCategory[category.id].orEmpty()
            val monthAmount = when (category.categoryType) {
                CATEGORY_TYPE_INCOME -> monthIncomesByCategory[category.id]
                    .orEmpty()
                    .fold(0L) { acc, income -> acc + income.amount }
                else -> monthExpensesByCategory[category.id]
                    .orEmpty()
                    .fold(0L) { acc, expense -> acc + expense.amount }
            }
            val accent = category.color.toColorOrDefault()

            CategoryUiModel(
                id = category.id,
                name = category.name,
                iconKey = category.icon,
                isCustom = true,
                colorHex = category.color,
                categoryType = category.categoryType,
                isArchived = category.isArchived == 1L,
                transactionCount = if (category.categoryType == CATEGORY_TYPE_INCOME) {
                    allIncomes.size
                } else {
                    allExpenses.size
                },
                monthAmount = monthAmount,
                accent = accent,
            )
        }
}

private fun iconForKey(iconKey: String): ImageVector {
    return when (iconKey) {
        "shopping_cart" -> Icons.Rounded.ShoppingCart
        "restaurant" -> Icons.Rounded.Restaurant
        "flight" -> Icons.Rounded.Flight
        "pets" -> Icons.Rounded.Pets
        "local_hospital" -> Icons.Rounded.LocalHospital
        "health" -> Icons.Rounded.HealthAndSafety
        "home" -> Icons.Rounded.Home
        "directions_car" -> Icons.Rounded.DirectionsCar
        "work" -> Icons.Rounded.Work
        "coffee" -> Icons.Rounded.Coffee
        "savings" -> Icons.Rounded.Savings
        "person" -> Icons.Rounded.Person
        else -> Icons.Rounded.Category
    }
}

private fun Long.formatMinorUnits(currencySymbol: String = "€"): String {
    val isNegative = this < 0L
    val absolute = if (isNegative) -this else this
    val digits = absolute.toString().padStart(3, '0')
    val integerPart = digits.dropLast(2).ifBlank { "0" }
    val cents = digits.takeLast(2)

    return buildString {
        if (isNegative) append("-")
        append(currencySymbol)
        append(integerPart)
        append(".")
        append(cents)
    }
}

private fun currentMonthBoundsMillis(): Pair<Long, Long> {
    val timeZone = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(timeZone).date
    val firstDay = LocalDate(today.year, today.month, 1)
    val firstDayNextMonth = firstDay.plus(DatePeriod(months = 1))

    return firstDay.atStartOfDayIn(timeZone).toEpochMilliseconds() to
        firstDayNextMonth.atStartOfDayIn(timeZone).toEpochMilliseconds()
}

private fun buildCustomCategoryId(): String = IdGenerator.newId("custom-category")

private fun Int.isWideBentoCard(): Boolean {
    return this % 7 == 2 || this % 7 == 6
}

private val AccentPurple = Color(0xFF6F45E9)
private val DeleteRed = Color(0xFFE53935)

private val CategoryAccentPalette = listOf(
    Color(0xFF2FA66A),
    Color(0xFF6F45E9),
    Color(0xFF2388D9),
    Color(0xFFE46C42),
    Color(0xFFD63871),
    Color(0xFF009688),
    Color(0xFFE19A15),
    Color(0xFF8D6E63),
)

private val CategoryAccentPaletteHex = listOf(
    "#2FA66A",
    "#6F45E9",
    "#2388D9",
    "#E46C42",
    "#D63871",
    "#009688",
    "#E19A15",
    "#8D6E63",
)

private fun String.toColorOrDefault(): Color {
    val normalized = removePrefix("#")
    if (normalized.length != 6 && normalized.length != 8) {
        return AccentPurple
    }
    val value = normalized.toLongOrNull(16) ?: return AccentPurple
    return if (normalized.length == 6) {
        Color((0xFF000000 or value).toInt())
    } else {
        Color(value.toInt())
    }
}

@Immutable
private data class BentoPalette(
    val background: Brush,
    val glassSurface: Color,
    val glassStrong: Color,
    val glassSurfaceSoft: Color,
    val cardSurface: Color,
    val cardHighlight: Color,
    val panelSurface: Color,
    val iconSurface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val sheetBackground: Color,
)

@Composable
private fun rememberBentoPalette(): BentoPalette {
    val darkTheme = isSystemInDarkTheme()

    return remember(darkTheme) {
        if (darkTheme) {
            BentoPalette(
                background = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0B1020),
                        Color(0xFF131A2E),
                        Color(0xFF1A2133),
                    )
                ),
                glassSurface = Color(0xD9242C3F),
                glassStrong = Color(0xCC1E2638),
                glassSurfaceSoft = Color(0x992D374D),
                cardSurface = Color(0xCC1A2233),
                cardHighlight = Color(0xCC243047),
                panelSurface = Color(0x66283347),
                iconSurface = Color(0xA62D374D),
                textPrimary = Color(0xFFF5F7FC),
                textSecondary = Color(0xFFC0C8D9),
                textMuted = Color(0xFF8E99AF),
                sheetBackground = Color(0xFF12192A),
            )
        } else {
            BentoPalette(
                background = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF9F9FF),
                        Color(0xFFF4F7FF),
                        Color(0xFFFFFFFF),
                    )
                ),
                glassSurface = Color.White.copy(alpha = 0.76f),
                glassStrong = Color.White.copy(alpha = 0.70f),
                glassSurfaceSoft = Color.White.copy(alpha = 0.68f),
                cardSurface = Color.White.copy(alpha = 0.82f),
                cardHighlight = Color.White.copy(alpha = 0.78f),
                panelSurface = Color.White.copy(alpha = 0.56f),
                iconSurface = Color.White.copy(alpha = 0.68f),
                textPrimary = Color(0xFF15172E),
                textSecondary = Color(0xFF687083),
                textMuted = Color(0xFF8B91A1),
                sheetBackground = Color(0xFFF4F6FB),
            )
        }
    }
}
