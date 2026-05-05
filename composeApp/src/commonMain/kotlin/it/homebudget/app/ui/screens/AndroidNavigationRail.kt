package it.homebudget.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onImportCsv: () -> Unit,
    onExportCsv: () -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Open)
    val scope = rememberCoroutineScope()
    var csvExpanded by remember { mutableStateOf(false) }
    var backupExpanded by remember { mutableStateOf(false) }
    val dashboardLabel = stringResource(Res.string.dashboard)
    val categoriesLabel = stringResource(Res.string.categories)
    val csvLabel = stringResource(Res.string.csv)
    val backupMenuLabel = stringResource(Res.string.backup)
    val backupLabel = stringResource(Res.string.backup_to_google_drive)
    val restoreLabel = stringResource(Res.string.restore_from_google_drive)
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
                        label = { Text(backupMenuLabel) },
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.Save,
                                contentDescription = backupMenuLabel,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        badge = {
                            Icon(
                                imageVector = if (backupExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            backupExpanded = !backupExpanded
                        }
                    )

                    if (backupExpanded) {
                        SubmenuDrawerButton(
                            label = backupLabel,
                            icon = Icons.Filled.Save,
                            onClick = {
                                scope.launch {
                                    drawerState.close()
                                    onBackup()
                                }
                            }
                        )

                        SubmenuDrawerButton(
                            label = restoreLabel,
                            icon = Icons.Filled.Restore,
                            onClick = {
                                scope.launch {
                                    drawerState.close()
                                    onRestore()
                                }
                            }
                        )
                    }

                    NavigationDrawerItem(
                        selected = false,
                        label = { Text(csvLabel) },
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.FolderZip,
                                contentDescription = csvLabel,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        badge = {
                            Icon(
                                imageVector = if (csvExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            csvExpanded = !csvExpanded
                        }
                    )

                    if (csvExpanded) {
                        SubmenuDrawerButton(
                            label = importCsvLabel,
                            icon = Icons.Filled.FileUpload,
                            onClick = {
                                scope.launch {
                                    drawerState.close()
                                    onImportCsv()
                                }
                            }
                        )

                        SubmenuDrawerButton(
                            label = exportCsvLabel,
                            icon = Icons.Filled.FileDownload,
                            onClick = {
                                scope.launch {
                                    drawerState.close()
                                    onExportCsv()
                                }
                            }
                        )
                    }
                }
            }
        },
        content = {
            Box(modifier = Modifier.fillMaxSize())
        }
    )
}

@Composable
private fun SubmenuDrawerButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = label,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
