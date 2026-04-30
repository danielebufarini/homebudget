package it.homebudget.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.localization.LocalStrings
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.random.Random
import kotlin.time.Clock

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
    var showAddDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val categories by repository.getAllCategories().collectAsState(initial = emptyList())
    val strings = LocalStrings.current

    fun deleteCategory(categoryId: String) {
        scope.launch {
            runCatching {
                repository.deleteCategory(categoryId)
            }.onFailure {
                snackbarHostState.showSnackbar(strings.unableToDeleteCategory)
            }
        }
    }

    LaunchedEffect(repository) {
        repository.insertDefaultCategoriesIfEmpty()
    }

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
                    onDeleteCategory = ::deleteCategory
                )
            } else {
                AndroidCategoriesRecyclerView(
                    categories = categories,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    onDeleteCategory = ::deleteCategory
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
                    onDeleteCategory = ::deleteCategory
                )
            } else {
                AndroidCategoriesRecyclerView(
                    categories = categories,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    onDeleteCategory = ::deleteCategory
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
                            contentDescription = strings.addCategory
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

    if (showAddDialog) {
        var newCategoryName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(strings.addCategory) },
            text = {
                PlatformTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    label = strings.categoryName,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                val trimmedName = newCategoryName.trim()
                TextButton(
                    enabled = trimmedName.isNotEmpty(),
                    colors = homeBudgetTextButtonColors(),
                    onClick = {
                        scope.launch {
                            repository.insertCategory(
                                id = buildCategoryId(),
                                name = trimmedName,
                                icon = "category",
                                isCustom = true
                            )
                            showAddDialog = false
                        }
                    }
                ) {
                    Text(strings.add)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddDialog = false },
                    colors = homeBudgetTextButtonColors()
                ) {
                    Text(strings.cancel)
                }
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
    val scope = rememberCoroutineScope()
    val openCsvImport = rememberCsvImportLauncher { message ->
        scope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }
    var showNavigationRail by remember { mutableStateOf(false) }
    val strings = LocalStrings.current

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                if (showNavigationChrome) {
                    CenterAlignedTopAppBar(
                        title = { Text(strings.categories) },
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
                                    contentDescription = strings.categories
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
                                contentDescription = strings.addCategory
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
                onOpenDashboard = onBack,
                onOpenCalendar = {
                    navigator?.push(CalendarExpensesScreen())
                },
                onOpenCategories = {},
                onImportCsv = openCsvImport
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoriesList(
    categories: List<it.homebudget.app.database.Category>,
    modifier: Modifier = Modifier,
    onDeleteCategory: (String) -> Unit
) {
    val strings = LocalStrings.current

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
                    CategoryListItem(category = category)
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
    val strings = LocalStrings.current

    DeleteSwipeBackground(
        contentDescription = strings.deleteCategory,
        shape = if (isIos) RoundedCornerShape(20.dp) else MaterialTheme.shapes.medium
    )
}

@Composable
internal fun CategoryListItem(
    category: it.homebudget.app.database.Category,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current

    PlatformCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(strings.categoryName(category.id, category.name, category.isCustom))
            Text(
                text = if (category.isCustom == 1L) strings.customCategory else strings.defaultCategory,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun buildCategoryId(): String {
    return "custom_${Clock.System.now().toEpochMilliseconds()}_${Random.nextLong()}"
}
