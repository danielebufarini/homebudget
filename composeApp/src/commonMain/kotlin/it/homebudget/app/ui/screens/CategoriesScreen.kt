package it.homebudget.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import homebudget.composeapp.generated.resources.*
import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.localization.formatResourceArgs
import it.homebudget.app.localization.localizedCategoryName
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

class CategoriesScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current

        CategoriesRoute(
            onBack = { navigator?.pop() },
            showNavigationChrome = true,
            showFab = true
        )
    }
}

@Composable
fun CategoriesRoute(
    onBack: () -> Unit,
    showNavigationChrome: Boolean,
    showFab: Boolean,
    addCategoryRequestKey: Int = 0
) {
    val repository: ExpenseRepository = koinInject()
    val isIos = rememberIsIosPlatform()
    var categoryBeingEdited by remember { mutableStateOf<it.homebudget.app.database.Category?>(null) }
    var categoryPendingDelete by remember { mutableStateOf<it.homebudget.app.database.Category?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val categories by repository.getAllCategories().collectAsState(initial = emptyList())
    val addCategoryLabel = stringResource(Res.string.add_category)
    val editCategoryLabel = stringResource(Res.string.edit_category)
    val addLabel = stringResource(Res.string.add)
    val updateLabel = stringResource(Res.string.update)
    val deleteItemConfirmationMessageTemplate = stringResource(Res.string.delete_item_confirmation_message)
    val unableToDeleteCategoryLabel = stringResource(Res.string.unable_to_delete_category)

    fun deleteCategory(categoryId: String) {
        scope.launch {
            runCatching {
                repository.deleteCategory(categoryId)
            }.onFailure {
                snackbarHostState.showSnackbar(unableToDeleteCategoryLabel)
            }
        }
    }

    EnsureDefaultCategoriesInserted(repository)

    LaunchedEffect(addCategoryRequestKey) {
        if (addCategoryRequestKey > 0) {
            showAddDialog = true
        }
    }

    if (showNavigationChrome) {
        CategoriesScreenScaffold(
            showNavigationChrome = true,
            showFab = showFab,
            onBack = onBack,
            onShowAddDialog = { showAddDialog = true },
            snackbarHostState = snackbarHostState
        ) { padding ->
            if (isIos) {
                CategoriesList(
                    categories = categories,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    onDeleteCategory = ::deleteCategory,
                    onEditCategory = { categoryBeingEdited = it }
                )
            } else {
                AndroidCategoriesRecyclerView(
                    categories = categories,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    onDeleteCategory = { categoryId ->
                        categoryPendingDelete = categories.find { it.id == categoryId }
                    },
                    onEditCategory = { categoryBeingEdited = it }
                )
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isIos) {
                CategoriesList(
                    categories = categories,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    onDeleteCategory = ::deleteCategory,
                    onEditCategory = { categoryBeingEdited = it }
                )
            } else {
                AndroidCategoriesRecyclerView(
                    categories = categories,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    onDeleteCategory = { categoryId ->
                        categoryPendingDelete = categories.find { it.id == categoryId }
                    },
                    onEditCategory = { categoryBeingEdited = it }
                )
            }

            if (showFab) {
                if (isIos) {
                    FloatingActionButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                    ) {
                        Text("+")
                    }
                } else {
                    FloatingActionButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = addCategoryLabel
                        )
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )
        }
    }

    if (showAddDialog || categoryBeingEdited != null) {
        val editingCategory = categoryBeingEdited
        AddCategorySheet(
            onDismiss = {
                showAddDialog = false
                categoryBeingEdited = null
            },
            title = if (editingCategory == null) addCategoryLabel else editCategoryLabel,
            confirmLabel = if (editingCategory == null) addLabel else updateLabel,
            initialName = editingCategory?.name.orEmpty(),
            initialIconKey = editingCategory?.icon ?: DEFAULT_CATEGORY_ICON_KEY,
            onConfirm = { name, iconKey ->
                scope.launch {
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
                            icon = iconKey
                        )
                    }
                    showAddDialog = false
                    categoryBeingEdited = null
                }
            }
        )
    }

    categoryPendingDelete?.let { category ->
        DeleteConfirmationDialog(
            message = deleteItemConfirmationMessageTemplate.formatResourceArgs(localizedCategoryName(category)),
            onDelete = {
                categoryPendingDelete = null
                deleteCategory(category.id)
            },
            onDismiss = {
                categoryPendingDelete = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoriesScreenScaffold(
    showNavigationChrome: Boolean,
    showFab: Boolean,
    onBack: () -> Unit,
    onShowAddDialog: () -> Unit,
    snackbarHostState: SnackbarHostState,
    content: @Composable (PaddingValues) -> Unit
) {
    val isIos = rememberIsIosPlatform()
    val navigator = LocalNavigator.current
    var showNavigationRail by remember { mutableStateOf(false) }
    var showCloudBackupSheet by remember { mutableStateOf(false) }
    var showCsvTransferSheet by remember { mutableStateOf(false) }
    val addCategoryLabel = stringResource(Res.string.add_category)
    val categoriesLabel = stringResource(Res.string.categories)

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidDataTransferUi(
            snackbarHostState = snackbarHostState,
            showCloudBackupSheet = showCloudBackupSheet,
            onDismissCloudBackupSheet = { showCloudBackupSheet = false },
            showCsvTransferSheet = showCsvTransferSheet,
            onDismissCsvTransferSheet = { showCsvTransferSheet = false }
        )

        Scaffold(
            topBar = {
                if (showNavigationChrome) {
                    CenterAlignedTopAppBar(
                        title = { Text(categoriesLabel) },
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    if (!isIos) {
                                        showNavigationRail = true
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Menu,
                                    contentDescription = categoriesLabel
                                )
                            }
                        }
                    )
                }
            },
            floatingActionButton = {
                if (showFab) {
                    if (isIos) {
                        FloatingActionButton(onClick = onShowAddDialog) {
                            Text("+")
                        }
                    } else {
                        FloatingActionButton(
                            onClick = onShowAddDialog
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = addCategoryLabel
                            )
                        }
                    }
                }
            },
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            }
        ) { padding ->
            content(padding)
        }

        if (!isIos && showNavigationRail) {
            AndroidNavigationRailOverlay(
                selectedDestination = AndroidNavigationDestination.Categories,
                onDismiss = { showNavigationRail = false },
                onOpenCategories = {},
                onOpenFullCloudBackup = { showCloudBackupSheet = true },
                onOpenCsvTransfer = { showCsvTransferSheet = true }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoriesList(
    categories: List<it.homebudget.app.database.Category>,
    modifier: Modifier = Modifier,
    onDeleteCategory: (String) -> Unit,
    onEditCategory: (it.homebudget.app.database.Category) -> Unit
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = categories,
            key = { category -> category.id }
        ) { category ->
            if (category.isCustom == 1L) {
                val dismissState = rememberSwipeToDeleteBoxState(
                    itemId = category.id,
                    onDeleteItem = onDeleteCategory
                )

                SwipeToDismissBox(
                    state = dismissState,
                    enableDismissFromStartToEnd = false,
                    backgroundContent = {
                        if (dismissState.dismissDirection == SwipeToDismissBoxValue.Settled) {
                            Spacer(modifier = Modifier.fillMaxSize())
                        } else {
                            DeleteCategoryBackground()
                        }
                    }
                ) {
                    CategoryListItem(
                        category = category,
                        onClick = { onEditCategory(category) }
                    )
                }
            } else {
                CategoryListItem(category = category)
            }
        }
    }
}

@Composable
internal fun DeleteCategoryBackground() {
    val isIos = rememberIsIosPlatform()
    val deleteCategoryLabel = stringResource(Res.string.delete_category)

    DeleteSwipeBackground(
        contentDescription = deleteCategoryLabel,
        shape = if (isIos) RoundedCornerShape(20.dp) else MaterialTheme.shapes.medium
    )
}

@Composable
internal fun CategoryListItem(
    category: it.homebudget.app.database.Category,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val customCategoryLabel = stringResource(Res.string.custom_category)
    val defaultCategoryLabel = stringResource(Res.string.default_category)

    PlatformCard(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                CategoryLabel(
                    iconKey = category.icon,
                    colorKey = category.id,
                    text = localizedCategoryName(category),
                    maxLines = 1
                )
                Text(
                    text = if (category.isCustom == 1L) customCategoryLabel else defaultCategoryLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (onClick != null) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(Res.string.edit_category),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
