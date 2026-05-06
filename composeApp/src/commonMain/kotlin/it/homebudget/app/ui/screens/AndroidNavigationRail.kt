package it.homebudget.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.categories
import homebudget.composeapp.generated.resources.csv
import homebudget.composeapp.generated.resources.settings
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

internal enum class AndroidNavigationDestination {
    Dashboard,
    Categories,
    Settings
}

@Composable
internal fun AndroidNavigationRailOverlay(
    selectedDestination: AndroidNavigationDestination,
    onDismiss: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenCsvTransfer: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Open)
    val scope = rememberCoroutineScope()
    val categoriesLabel = stringResource(Res.string.categories)
    val csvLabel = stringResource(Res.string.csv)
    val settingsLabel = stringResource(Res.string.settings)

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

                    NavigationDrawerItem(
                        selected = selectedDestination == AndroidNavigationDestination.Settings,
                        label = { Text(settingsLabel) },
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = settingsLabel,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                if (selectedDestination != AndroidNavigationDestination.Settings) {
                                    onOpenSettings()
                                }
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
