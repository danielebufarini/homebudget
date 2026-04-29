package it.homebudget.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

internal enum class AndroidNavigationDestination {
    Dashboard,
    Categories
}

@Composable
internal fun AndroidNavigationRailOverlay(
    selectedDestination: AndroidNavigationDestination,
    onDismiss: () -> Unit,
    onOpenDashboard: () -> Unit,
    onOpenCategories: () -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Open)
    val scope = rememberCoroutineScope()

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
                        label = { Text("Dashboard") },
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.Dashboard,
                                contentDescription = "Dashboard",
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
                        label = { Text("Categories") },
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.Category,
                                contentDescription = "Categories",
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
                }
            }
        },
        content = {
            Box(modifier = Modifier.fillMaxSize())
        }
    )
}
