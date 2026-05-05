package it.homebudget.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import homebudget.composeapp.generated.resources.*
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
    onOpenDashboard: () -> Unit,
    onOpenCategories: () -> Unit,
    onImportCsv: () -> Unit,
    onExportCsv: () -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Open)
    val scope = rememberCoroutineScope()
    val dashboardLabel = stringResource(Res.string.dashboard)
    val categoriesLabel = stringResource(Res.string.categories)
    val importCsvLabel = stringResource(Res.string.import_csv)
    val exportCsvLabel = stringResource(Res.string.export_csv)

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
                        selected = selectedDestination == AndroidNavigationDestination.Dashboard,
                        label = { Text(dashboardLabel) },
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.Dashboard,
                                contentDescription = dashboardLabel,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                if (selectedDestination != AndroidNavigationDestination.Dashboard) {
                                    onOpenDashboard()
                                }
                            }
                        }
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
                        label = { Text(importCsvLabel) },
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.FileUpload,
                                contentDescription = importCsvLabel,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                onImportCsv()
                            }
                        }
                    )

                    NavigationDrawerItem(
                        selected = false,
                        label = { Text(exportCsvLabel) },
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.FileDownload,
                                contentDescription = exportCsvLabel,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                onExportCsv()
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
