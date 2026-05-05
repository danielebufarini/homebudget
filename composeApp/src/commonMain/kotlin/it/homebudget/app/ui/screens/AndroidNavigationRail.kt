package it.homebudget.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.backup
import homebudget.composeapp.generated.resources.categories
import homebudget.composeapp.generated.resources.csv
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

internal enum class AndroidNavigationDestination {
    Dashboard,
    Categories
}

@Composable
internal fun AndroidNavigationRailOverlay(
    selectedDestination: AndroidNavigationDestination,
    onDismiss: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenFullCloudBackup: () -> Unit,
    onOpenCsvTransfer: () -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Open)
    val scope = rememberCoroutineScope()
    val categoriesLabel = stringResource(Res.string.categories)
    val csvLabel = stringResource(Res.string.csv)
    val backupMenuLabel = stringResource(Res.string.backup)

    LaunchedEffect(drawerState.isClosed) {
        if (drawerState.isClosed) {
            onDismiss()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "HomeBudget",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )

                    NavigationDrawerItem(
                        selected = selectedDestination == AndroidNavigationDestination.Categories,
                        label = { Text(categoriesLabel) },
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.Category,
                                contentDescription = categoriesLabel,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                if (selectedDestination != AndroidNavigationDestination.Categories) {
                                    onOpenCategories()
                                }
                            }
                        }
                    )

                    NavigationDrawerItem(
                        selected = false,
                        label = { Text(backupMenuLabel) },
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.Cloud,
                                contentDescription = backupMenuLabel,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                onOpenFullCloudBackup()
                            }
                        }
                    )

                    NavigationDrawerItem(
                        selected = false,
                        label = { Text(csvLabel) },
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.ImportExport,
                                contentDescription = csvLabel,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                onOpenCsvTransfer()
                            }
                        }
                    )
                }
            }
        },
        content = {
            Box(modifier = Modifier.fillMaxSize())
        }
    )
}
