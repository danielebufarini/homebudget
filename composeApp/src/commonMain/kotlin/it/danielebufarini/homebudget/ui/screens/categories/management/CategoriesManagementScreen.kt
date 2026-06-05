package it.danielebufarini.homebudget.ui.screens.categories.management

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.categories
import homebudget.composeapp.generated.resources.categories_add_content_description
import it.danielebufarini.homebudget.data.CategoryManagementRepository
import it.danielebufarini.homebudget.data.ExpenseReadRepository
import it.danielebufarini.homebudget.data.IncomeReadRepository
import it.danielebufarini.homebudget.ui.screens.EdgeToEdgeTopBarOverlay
import it.danielebufarini.homebudget.ui.screens.platform.rememberIsIosPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
internal fun CategoriesManagementRoute(
    categoryRepository: CategoryManagementRepository = koinInject(),
    expenseReadRepository: ExpenseReadRepository = koinInject(),
    incomeReadRepository: IncomeReadRepository = koinInject(),
    onBack: (() -> Unit)? = null,
) {
    LaunchedEffect(categoryRepository) {
        withContext(Dispatchers.Default) {
            categoryRepository.seedStarterCategoriesIfEmpty()
        }
    }

    val categoryCardsFlow = remember(categoryRepository, expenseReadRepository, incomeReadRepository) {
        combine(
            categoryRepository.getAllCategories(),
            expenseReadRepository.getExpenseCategoryUsageCounts(),
            incomeReadRepository.getIncomeCategoryUsageCounts(),
        ) { categories, expenseUsageCounts, incomeUsageCounts ->
            buildCategoryUiModels(
                categories = categories,
                expenseUsageCounts = expenseUsageCounts,
                incomeUsageCounts = incomeUsageCounts,
            )
        }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
    }
    val categoryCards by categoryCardsFlow.collectAsState(initial = emptyList())

    val routeState = rememberCategoriesManagementStateStore()

    val scope = rememberCoroutineScope()
    val isIos = rememberIsIosPlatform()
    val categoriesLabel = stringResource(Res.string.categories)
    val addCategoryContentDescription = stringResource(Res.string.categories_add_content_description)
    val addCategory = routeState::startAdd

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
                query = routeState.query,
                selectedFilter = routeState.selectedFilter,
                sortAscending = routeState.sortAscending,
                onQueryChange = routeState::updateQuery,
                onSortToggle = routeState::toggleSort,
                onFilterChange = routeState::selectFilter,
                onBack = null,
                onAdd = addCategory,
                onEdit = routeState::startEdit,
                onDelete = routeState::requestDelete,
                externalHeaderOffset = padding.calculateTopPadding(),
            )
        }
    } else {
        CategoriesManagementContent(
            categories = categoryCards,
            query = routeState.query,
            selectedFilter = routeState.selectedFilter,
            sortAscending = routeState.sortAscending,
            onQueryChange = routeState::updateQuery,
            onSortToggle = routeState::toggleSort,
            onFilterChange = routeState::selectFilter,
            onBack = onBack,
            onAdd = addCategory,
            onEdit = routeState::startEdit,
            onDelete = routeState::requestDelete,
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

    routeState.editorTarget?.let { target ->
        CategoryEditorSheet(
            category = target,
            onDismiss = routeState::dismissEditor,
            onSave = { edited ->
                routeState.clearAfterSave()
                scope.launch {
                    if (edited.id.isBlank()) {
                        categoryRepository.insertCategory(
                            id = buildCategoryId(),
                            name = edited.name.trim(),
                            icon = edited.iconKey,
                            color = edited.colorHex,
                            categoryType = edited.categoryType,
                            isArchived = false,
                        )
                    } else {
                        categoryRepository.updateCategory(
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
                routeState.requestDeleteFromEditor(target)
            },
        )
    }

    routeState.deleteTarget?.let { target ->
        if (target.transactionCount > 0) {
            UsedCategoryDeleteDialog(
                category = target,
                onDismiss = routeState::dismissDelete,
                onArchive = {
                    routeState.dismissDelete()
                    scope.launch {
                        categoryRepository.setCategoryArchived(target.id, true)
                    }
                },
                onMoveTransactions = {
                    routeState.startMoveFromDelete(target)
                },
            )
        } else {
            DeleteCategoryDialog(
                category = target,
                onDismiss = routeState::dismissDelete,
                onConfirm = {
                    routeState.dismissDelete()
                    scope.launch {
                        categoryRepository.deleteCategory(target.id)
                    }
                },
            )
        }
    }

    routeState.moveTarget?.let { sourceCategory ->
        MoveCategoryTransactionsSheet(
            sourceCategory = sourceCategory,
            availableCategories = categoryCards.filter { candidate ->
                candidate.id != sourceCategory.id &&
                    candidate.categoryType == sourceCategory.categoryType &&
                    !candidate.isArchived
            },
            onDismiss = routeState::dismissMove,
            onConfirm = { targetCategoryId ->
                routeState.dismissMove()
                scope.launch {
                    categoryRepository.reassignCategoryTransactions(
                        sourceCategoryId = sourceCategory.id,
                        targetCategoryId = targetCategoryId,
                    )
                }
            },
        )
    }
}
