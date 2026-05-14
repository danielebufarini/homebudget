package it.homebudget.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import homebudget.composeapp.generated.resources.custom_category
import homebudget.composeapp.generated.resources.delete
import homebudget.composeapp.generated.resources.delete_category_in_use
import homebudget.composeapp.generated.resources.delete_item_confirmation_message
import homebudget.composeapp.generated.resources.edit_category
import homebudget.composeapp.generated.resources.no_custom_categories_yet
import homebudget.composeapp.generated.resources.unable_to_delete_category
import homebudget.composeapp.generated.resources.unable_to_save_category
import homebudget.composeapp.generated.resources.update
import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.database.Category
import it.homebudget.app.localization.formatResourceArgs
import it.homebudget.app.localization.rememberCategoryNameResolver
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
        val repository: ExpenseRepository = koinInject()
        val isIos = rememberIsIosPlatform()
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }
        val addCategoryLabel = stringResource(Res.string.add_category)
        val addLabel = stringResource(Res.string.add)
        val backLabel = stringResource(Res.string.back)
        val categoriesLabel = stringResource(Res.string.categories)
        val customCategoryLabel = stringResource(Res.string.custom_category)
        val deleteCategoryInUseLabel = stringResource(Res.string.delete_category_in_use)
        val deleteItemConfirmationMessageTemplate = stringResource(Res.string.delete_item_confirmation_message)
        val editCategoryLabel = stringResource(Res.string.edit_category)
        val noCustomCategoriesLabel = stringResource(Res.string.no_custom_categories_yet)
        val unableToDeleteCategoryLabel = stringResource(Res.string.unable_to_delete_category)
        val unableToSaveCategoryLabel = stringResource(Res.string.unable_to_save_category)
        val updateCategoryLabel = stringResource(Res.string.update)
        val resolveCategoryName = rememberCategoryNameResolver()

        var showAddCategorySheet by remember { mutableStateOf(false) }
        var categoryBeingEdited by remember { mutableStateOf<Category?>(null) }
        var categoryPendingDelete by remember { mutableStateOf<Category?>(null) }

        val categories by repository.getAllCategories().collectAsState(initial = emptyList())
        val customCategories = remember(categories, resolveCategoryName) {
            categories
                .filter { it.isCustom == 1L }
                .sortedBy { category ->
                    resolveCategoryName(category.id, category.name, category.isCustom).lowercase()
                }
        }

        EnsureDefaultCategoriesInserted(repository)

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
            if (customCategories.isEmpty()) {
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
                            text = customCategoryLabel,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = noCustomCategoriesLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = customCategories,
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
                                categoryName = resolveCategoryName(category.id, category.name, category.isCustom),
                                onClick = { categoryBeingEdited = category }
                            )
                        }
                    }

                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 88.dp)
                        )
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
                    resolveCategoryName(category.id, category.name, category.isCustom)
                }.orEmpty(),
                initialIconKey = editingCategory?.icon ?: DEFAULT_CATEGORY_ICON_KEY,
                onConfirm = { name, iconKey ->
                    scope.launch {
                        runCatching {
                            if (editingCategory == null) {
                                repository.insertCategory(
                                    id = buildCustomCategoryId(),
                                    name = name,
                                    icon = iconKey,
                                    isCustom = true
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
            val categoryName = resolveCategoryName(category.id, category.name, category.isCustom)
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
