package it.homebudget.app.ui.screens.categories.management

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.categories
import homebudget.composeapp.generated.resources.categories_add_content_description
import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.ui.screens.EdgeToEdgeTopBarOverlay
import it.homebudget.app.ui.screens.clearActiveIosCategoriesManagementAddHandler
import it.homebudget.app.ui.screens.rememberIsIosPlatform
import it.homebudget.app.ui.screens.setActiveIosCategoriesManagementAddHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

object CategoriesManagementScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        CategoriesManagementRoute(onBack = { navigator?.pop() })
    }
}

@Suppress("UNUSED_VALUE")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesManagementRoute(
    repository: ExpenseRepository = koinInject(),
    onBack: (() -> Unit)? = null,
) {
    LaunchedEffect(repository) {
        repository.seedStarterCategoriesIfEmpty()
    }

    val categoryCardsFlow = remember(repository) {
        combine(
            repository.getAllCategories(),
            repository.getAllExpenses(),
            repository.getAllIncomes(),
        ) { categories, expenses, incomes ->
            buildCategoryUiModels(
                categories = categories,
                expenses = expenses,
                incomes = incomes,
            )
        }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
    }
    val categoryCards by categoryCardsFlow.collectAsState(initial = emptyList())

    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(CategoryFilter.All.name) }
    var sortAscending by rememberSaveable { mutableStateOf(true) }
    var editorTarget by remember { mutableStateOf<CategoryUiModel?>(null) }
    var deleteTarget by remember { mutableStateOf<CategoryUiModel?>(null) }
    var moveTarget by remember { mutableStateOf<CategoryUiModel?>(null) }

    val scope = rememberCoroutineScope()
    val isIos = rememberIsIosPlatform()
    val categoriesLabel = stringResource(Res.string.categories)
    val addCategoryContentDescription = stringResource(Res.string.categories_add_content_description)
    val addCategory = { editorTarget = CategoryUiModel.newEmpty() }

    if (!isIos && onBack != null) {
        EdgeToEdgeTopBarOverlay(
            topBar = { modifier ->
                CenterAlignedTopAppBar(
                    modifier = modifier,
                    title = { Text(categoriesLabel) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                    ),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = null,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = addCategory) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = addCategoryContentDescription,
                            )
                        }
                    },
                )
            },
        ) { padding ->
            CategoriesManagementContent(
                categories = categoryCards,
                query = query,
                selectedFilter = CategoryFilter.valueOf(filter),
                sortAscending = sortAscending,
                onQueryChange = { query = it },
                onSortToggle = { sortAscending = !sortAscending },
                onFilterChange = { filter = it.name },
                onBack = null,
                onAdd = addCategory,
                onEdit = { category -> editorTarget = category },
                onDelete = { category -> deleteTarget = category },
                externalHeaderOffset = padding.calculateTopPadding(),
            )
        }
    } else {
        CategoriesManagementContent(
            categories = categoryCards,
            query = query,
            selectedFilter = CategoryFilter.valueOf(filter),
            sortAscending = sortAscending,
            onQueryChange = { query = it },
            onSortToggle = { sortAscending = !sortAscending },
            onFilterChange = { filter = it.name },
            onBack = onBack,
            onAdd = addCategory,
            onEdit = { category -> editorTarget = category },
            onDelete = { category -> deleteTarget = category },
        )
    }

    SideEffect {
        if (onBack == null) {
            setActiveIosCategoriesManagementAddHandler { addCategory() }
        }
    }
    DisposableEffect(onBack) {
        onDispose {
            if (onBack == null) {
                clearActiveIosCategoriesManagementAddHandler()
            }
        }
    }

    editorTarget?.let { target ->
        CategoryEditorSheet(
            category = target,
            onDismiss = { editorTarget = null },
            onSave = { edited ->
                editorTarget = null
                scope.launch {
                    if (edited.id.isBlank()) {
                        repository.insertCategory(
                            id = buildCategoryId(),
                            name = edited.name.trim(),
                            icon = edited.iconKey,
                            color = edited.colorHex,
                            categoryType = edited.categoryType,
                            isArchived = false,
                        )
                    } else {
                        repository.updateCategory(
                            id = edited.id,
                            name = edited.name.trim(),
                            icon = edited.iconKey,
                            color = edited.colorHex,
                            categoryType = edited.categoryType,
                        )
                    }
                }
            },
            onDelete = {
                if (target.id.isNotBlank()) {
                    deleteTarget = target
                    editorTarget = null
                }
            },
        )
    }

    deleteTarget?.let { target ->
        if (target.transactionCount > 0) {
            UsedCategoryDeleteDialog(
                category = target,
                onDismiss = { deleteTarget = null },
                onArchive = {
                    deleteTarget = null
                    scope.launch {
                        repository.setCategoryArchived(target.id, true)
                    }
                },
                onMoveTransactions = {
                    moveTarget = target
                    deleteTarget = null
                },
            )
        } else {
            DeleteCategoryDialog(
                category = target,
                onDismiss = { deleteTarget = null },
                onConfirm = {
                    deleteTarget = null
                    scope.launch {
                        repository.deleteCategory(target.id)
                    }
                },
            )
        }
    }

    moveTarget?.let { sourceCategory ->
        MoveCategoryTransactionsSheet(
            sourceCategory = sourceCategory,
            availableCategories = categoryCards.filter { candidate ->
                candidate.id != sourceCategory.id &&
                    candidate.categoryType == sourceCategory.categoryType &&
                    !candidate.isArchived
            },
            onDismiss = { moveTarget = null },
            onConfirm = { targetCategoryId ->
                moveTarget = null
                scope.launch {
                    repository.reassignCategoryTransactions(
                        sourceCategoryId = sourceCategory.id,
                        targetCategoryId = targetCategoryId,
                    )
                }
            },
        )
    }
}
