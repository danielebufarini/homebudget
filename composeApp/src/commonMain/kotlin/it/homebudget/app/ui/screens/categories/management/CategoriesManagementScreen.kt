package it.homebudget.app.ui.screens.categories.management

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import it.homebudget.app.data.ExpenseRepository
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

object CategoriesManagementScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        CategoriesManagementRoute(onBack = { navigator?.pop() })
    }
}

@Suppress("UNUSED_VALUE")
@Composable
fun CategoriesManagementRoute(
    repository: ExpenseRepository = koinInject(),
    onBack: (() -> Unit)? = null,
) {
    LaunchedEffect(repository) {
        repository.seedStarterCategoriesIfEmpty()
    }

    val categories by repository.getAllCategories().collectAsState(initial = emptyList())
    val expenses by repository.getAllExpenses().collectAsState(initial = emptyList())
    val incomes by repository.getAllIncomes().collectAsState(initial = emptyList())

    val categoryCards = remember(categories, expenses, incomes) {
        buildCategoryUiModels(
            categories = categories,
            expenses = expenses,
            incomes = incomes,
        )
    }

    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(CategoryFilter.All.name) }
    var sortAscending by rememberSaveable { mutableStateOf(true) }
    var editorTarget by remember { mutableStateOf<CategoryUiModel?>(null) }
    var deleteTarget by remember { mutableStateOf<CategoryUiModel?>(null) }
    var moveTarget by remember { mutableStateOf<CategoryUiModel?>(null) }

    val scope = rememberCoroutineScope()

    CategoriesManagementContent(
        categories = categoryCards,
        query = query,
        selectedFilter = CategoryFilter.valueOf(filter),
        sortAscending = sortAscending,
        onQueryChange = { query = it },
        onSortToggle = { sortAscending = !sortAscending },
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
