package it.danielebufarini.homebudget.ui.screens.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.add
import homebudget.composeapp.generated.resources.add_category
import homebudget.composeapp.generated.resources.back
import homebudget.composeapp.generated.resources.categories
import homebudget.composeapp.generated.resources.categories_empty_all
import homebudget.composeapp.generated.resources.categories_subtitle
import homebudget.composeapp.generated.resources.delete
import homebudget.composeapp.generated.resources.delete_item_confirmation_message
import homebudget.composeapp.generated.resources.edit_category
import homebudget.composeapp.generated.resources.unable_to_delete_category
import homebudget.composeapp.generated.resources.unable_to_save_category
import homebudget.composeapp.generated.resources.update
import it.danielebufarini.homebudget.data.CategoryManagementRepository
import it.danielebufarini.homebudget.database.Category
import it.danielebufarini.homebudget.localization.formatResourceArgs
import it.danielebufarini.homebudget.localization.rememberCategoryNameResolver
import it.danielebufarini.homebudget.ui.screens.expenses.rememberSwipeToDeleteBoxState
import it.danielebufarini.homebudget.ui.screens.platform.PlatformCard
import it.danielebufarini.homebudget.ui.screens.platform.rememberIsIosPlatform
import it.danielebufarini.homebudget.ui.screens.transactions.DeleteConfirmationDialog
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

class CategoriesScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        RouteContent(
            showNavigationChrome = true,
            onClose = { navigator?.pop() }
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun RouteContent(
        showNavigationChrome: Boolean,
        onClose: () -> Unit
    ) {
        val repository: CategoryManagementRepository = koinInject()
        val isIos = rememberIsIosPlatform()
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }
        val addCategoryLabel = stringResource(Res.string.add_category)
        val addLabel = stringResource(Res.string.add)
        val backLabel = stringResource(Res.string.back)
        val categoriesLabel = stringResource(Res.string.categories)
        val emptyCategoriesLabel = stringResource(Res.string.categories_empty_all)
        val categoriesSubtitle = stringResource(Res.string.categories_subtitle)
        val deleteItemConfirmationMessageTemplate = stringResource(Res.string.delete_item_confirmation_message)
        val editCategoryLabel = stringResource(Res.string.edit_category)
        val unableToDeleteCategoryLabel = stringResource(Res.string.unable_to_delete_category)
        val unableToSaveCategoryLabel = stringResource(Res.string.unable_to_save_category)
        val updateCategoryLabel = stringResource(Res.string.update)
        val resolveCategoryName = rememberCategoryNameResolver()

        var showAddCategorySheet by remember { mutableStateOf(false) }
        var categoryBeingEdited by remember { mutableStateOf<Category?>(null) }
        var categoryPendingDelete by remember { mutableStateOf<Category?>(null) }

        val categories by repository.getAllCategories().collectAsState(initial = emptyList())
        val visibleCategories = remember(categories, resolveCategoryName) {
            categories
                .filter { it.isArchived != 1L }
                .sortedBy { category ->
                    resolveCategoryName(category.id, category.name).lowercase()
                }
        }

        EnsureStarterCategoriesSeeded(repository)

        Scaffold(
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            },
            topBar = {
                if (showNavigationChrome) {
                    TopAppBar(
                        title = {
                            Text(categoriesLabel)
                        },
                        navigationIcon = {
                            if (isIos) {
                                TextButton(onClick = onClose) {
                                    Text(backLabel)
                                }
                            }
                        }
                    )
                }
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showAddCategorySheet = true },
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = addCategoryLabel
                    )
                }
            }
        ) { padding ->
            if (visibleCategories.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = emptyCategoriesLabel,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = categoriesSubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                val listViewportModifier = if (isIos) {
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp)
                } else {
                    Modifier.fillMaxSize()
                }
                val listContentPadding = if (isIos) {
                    PaddingValues(bottom = 88.dp)
                } else {
                    PaddingValues(
                        start = 16.dp,
                        top = padding.calculateTopPadding() + 16.dp,
                        end = 16.dp,
                        bottom = padding.calculateBottomPadding() + 88.dp
                    )
                }

                LazyColumn(
                    modifier = listViewportModifier,
                    contentPadding = listContentPadding,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = visibleCategories,
                        key = { it.id }
                    ) { category ->
                        val dismissState = rememberSwipeToDeleteBoxState(
                            itemId = category.id,
                            onDeleteItem = {
                                categoryPendingDelete = category
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            backgroundContent = {
                                if (dismissState.dismissDirection == SwipeToDismissBoxValue.Settled) {
                                    Box(modifier = Modifier.fillMaxSize())
                                } else {
                                    DeleteCategoryBackground(
                                        label = stringResource(Res.string.delete)
                                    )
                                }
                            }
                        ) {
                            CategoryRow(
                                category = category,
                                categoryName = resolveCategoryName(category.id, category.name),
                                onClick = { categoryBeingEdited = category }
                            )
                        }
                    }
                }
            }
        }

        if (showAddCategorySheet || categoryBeingEdited != null) {
            val editingCategory = categoryBeingEdited
            AddCategorySheet(
                onDismiss = {
                    showAddCategorySheet = false
                    categoryBeingEdited = null
                },
                title = if (editingCategory == null) addCategoryLabel else editCategoryLabel,
                confirmLabel = if (editingCategory == null) addLabel else updateCategoryLabel,
                initialName = editingCategory?.let { category ->
                    resolveCategoryName(category.id, category.name)
                }.orEmpty(),
                initialIconKey = editingCategory?.icon ?: DEFAULT_CATEGORY_ICON_KEY,
                onConfirm = { name, iconKey ->
                    scope.launch {
                        runCatching {
                            if (editingCategory == null) {
                                repository.insertCategory(
                                    id = buildCategoryId(),
                                    name = name,
                                    icon = iconKey
                                )
                            } else {
                                repository.updateCategory(
                                    id = editingCategory.id,
                                    name = name,
                                    icon = iconKey,
                                    color = editingCategory.color,
                                    categoryType = editingCategory.categoryType
                                )
                            }
                        }.onSuccess {
                            showAddCategorySheet = false
                            categoryBeingEdited = null
                        }.onFailure {
                            snackbarHostState.showSnackbar(unableToSaveCategoryLabel)
                        }
                    }
                }
            )
        }

        categoryPendingDelete?.let { category ->
            val categoryName = resolveCategoryName(category.id, category.name)
            DeleteConfirmationDialog(
                message = deleteItemConfirmationMessageTemplate.formatResourceArgs(categoryName),
                onDelete = {
                    categoryPendingDelete = null
                    scope.launch {
                        runCatching {
                            repository.deleteCategory(category.id)
                        }.onFailure {
                            snackbarHostState.showSnackbar(unableToDeleteCategoryLabel)
                        }
                    }
                },
                onDismiss = {
                    categoryPendingDelete = null
                }
            )
        }
    }
}

@Composable
private fun CategoryRow(
    category: Category,
    categoryName: String,
    onClick: () -> Unit
) {
    PlatformCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        CategoryLabel(
            iconKey = category.icon,
            text = categoryName,
            colorKey = category.id,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun DeleteCategoryBackground(label: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}
