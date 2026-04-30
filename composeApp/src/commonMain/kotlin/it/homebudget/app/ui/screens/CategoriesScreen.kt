package it.homebudget.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    val categories by repository.getAllCategories().collectAsState(initial = emptyList())
    val strings = LocalStrings.current

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
            onShowAddDialog = { showAddDialog = true }
        ) { padding ->
            if (isIos) {
                CategoriesList(
                    categories = categories,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp)
                )
            } else {
                AndroidCategoriesRecyclerView(
                    categories = categories,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp)
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
                        .padding(16.dp)
                )
            } else {
                AndroidCategoriesRecyclerView(
                    categories = categories,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
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
    content: @Composable (PaddingValues) -> Unit
) {
    val isIos = rememberIsIosPlatform()
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
            }
        ) { padding ->
            content(padding)
        }

        if (!isIos && showNavigationRail) {
            AndroidNavigationRailOverlay(
                selectedDestination = AndroidNavigationDestination.Categories,
                onDismiss = { showNavigationRail = false },
                onOpenDashboard = onBack,
                onOpenCategories = {}
            )
        }
    }
}

@Composable
private fun CategoriesList(
    categories: List<it.homebudget.app.database.Category>,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(categories) { category ->
            PlatformCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
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
    }
}

private fun buildCategoryId(): String {
    return "custom_${Clock.System.now().toEpochMilliseconds()}_${Random.nextLong()}"
}
