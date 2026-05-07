package it.homebudget.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.backup_restore_confirmation_message
import homebudget.composeapp.generated.resources.backup_restore_failed
import homebudget.composeapp.generated.resources.restore
import homebudget.composeapp.generated.resources.restore_backup
import homebudget.composeapp.generated.resources.skip
import it.homebudget.app.ui.screens.dashboard.DashboardScreen
import it.homebudget.app.ui.theme.AppTheme
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private sealed interface AppStartupUiState {
    data object Loading : AppStartupUiState
    data object Ready : AppStartupUiState
    data class Prompt(
        val value: StartupRestorePrompt,
        val errorMessage: String? = null
    ) : AppStartupUiState
}

@Composable
fun App() {
    val startupRestore: PlatformStartupRestore = koinInject()
    val scope = rememberCoroutineScope()
    var startupUiState by remember { mutableStateOf<AppStartupUiState>(AppStartupUiState.Loading) }
    val restoreTitle = stringResource(Res.string.restore_backup)
    val restoreLabel = stringResource(Res.string.restore)
    val skipLabel = stringResource(Res.string.skip)
    val restoreFailedMessage = stringResource(Res.string.backup_restore_failed)

    LaunchedEffect(startupRestore) {
        startupUiState = runCatching {
            when (val state = startupRestore.prepare()) {
                StartupRestoreState.Ready -> AppStartupUiState.Ready
                is StartupRestoreState.Pending -> AppStartupUiState.Prompt(state.prompt)
            }
        }.getOrElse {
            AppStartupUiState.Ready
        }
    }

    AppTheme {
        when (val state = startupUiState) {
            AppStartupUiState.Ready -> {
                Navigator(DashboardScreen()) {
                    CurrentScreen()
                }
            }
            AppStartupUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is AppStartupUiState.Prompt -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

                AlertDialog(
                    onDismissRequest = {},
                    title = { Text(restoreTitle) },
                    text = {
                        Text(
                            buildString {
                                append(
                                    stringResource(
                                        Res.string.backup_restore_confirmation_message,
                                        state.value.preview.categoriesCount,
                                        state.value.preview.expensesCount,
                                        state.value.preview.incomesCount
                                    )
                                )
                                state.errorMessage?.let { errorMessage ->
                                    append("\n\n")
                                    append(errorMessage)
                                }
                            }
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                scope.launch {
                                    startupUiState = AppStartupUiState.Loading
                                    startupUiState = runCatching {
                                        startupRestore.restorePending()
                                        AppStartupUiState.Ready
                                    }.getOrElse {
                                        AppStartupUiState.Prompt(
                                            value = state.value,
                                            errorMessage = restoreFailedMessage
                                        )
                                    }
                                }
                            }
                        ) {
                            Text(restoreLabel)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    runCatching {
                                        startupRestore.skipPending()
                                    }
                                    startupUiState = AppStartupUiState.Ready
                                }
                            }
                        ) {
                            Text(skipLabel)
                        }
                    }
                )
            }
        }
    }
}
