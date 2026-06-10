package it.danielebufarini.spesify

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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
import it.danielebufarini.spesify.data.RecurringTransactionService
import it.danielebufarini.spesify.ui.screens.dashboard.DashboardScreen
import it.danielebufarini.spesify.ui.screens.transactions.AddTransactionScreen
import it.danielebufarini.spesify.ui.screens.transactions.ExpenseEditorPrefill
import it.danielebufarini.spesify.ui.screens.transactions.TransactionEditorKind
import it.danielebufarini.spesify.ui.theme.AppTheme
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import spesify.composeapp.generated.resources.Res
import spesify.composeapp.generated.resources.backup_restore_confirmation_message
import spesify.composeapp.generated.resources.backup_restore_failed
import spesify.composeapp.generated.resources.restore
import spesify.composeapp.generated.resources.restore_backup
import spesify.composeapp.generated.resources.skip

private sealed interface AppStartupUiState {
    data object Loading : AppStartupUiState
    data object Ready : AppStartupUiState
    data class Prompt(
        val value: StartupRestorePrompt,
        val errorMessage: String? = null
    ) : AppStartupUiState
}

@Composable
fun App(
    openVoiceExpenseRequest: Int = 0,
    openExpenseEditorPrefill: ExpenseEditorPrefill? = null
) {
    val startupRestore: PlatformStartupRestore = koinInject()
    val recurringTransactionService: RecurringTransactionService = koinInject()
    val scope = rememberCoroutineScope()
    var startupUiState by remember { mutableStateOf<AppStartupUiState>(AppStartupUiState.Loading) }
    val restoreTitle = stringResource(Res.string.restore_backup)
    val restoreLabel = stringResource(Res.string.restore)
    val skipLabel = stringResource(Res.string.skip)
    val restoreFailedMessage = stringResource(Res.string.backup_restore_failed)

    LaunchedEffect(startupRestore, recurringTransactionService) {
        startupUiState = runCatching {
            when (val state = startupRestore.prepare()) {
                StartupRestoreState.Ready -> {
                    recurringTransactionService.ensureRecurringTransactionsGeneratedThroughDefaultWindow()
                    AppStartupUiState.Ready
                }
                is StartupRestoreState.Pending -> AppStartupUiState.Prompt(state.prompt)
            }
        }.getOrElse {
            AppStartupUiState.Ready
        }
    }

    AppTheme {
        when (val state = startupUiState) {
            AppStartupUiState.Ready -> {
                var lastHandledVoiceExpenseRequest by remember {
                    mutableIntStateOf(openVoiceExpenseRequest)
                }
                var lastHandledExpensePrefillRequestId by remember {
                    mutableStateOf<String?>(null)
                }

                Navigator(
                    DashboardScreen(openVoiceExpenseRequest = openVoiceExpenseRequest)
                ) { navigator ->
                    LaunchedEffect(openVoiceExpenseRequest) {
                        if (openVoiceExpenseRequest > lastHandledVoiceExpenseRequest) {
                            lastHandledVoiceExpenseRequest = openVoiceExpenseRequest
                            navigator.replaceAll(
                                DashboardScreen(openVoiceExpenseRequest = openVoiceExpenseRequest)
                            )
                        }
                    }
                    LaunchedEffect(openExpenseEditorPrefill) {
                        val prefill = openExpenseEditorPrefill ?: return@LaunchedEffect
                        if (prefill.requestId != lastHandledExpensePrefillRequestId) {
                            lastHandledExpensePrefillRequestId = prefill.requestId
                            navigator.push(
                                AddTransactionScreen(
                                    initialKind = TransactionEditorKind.Expense,
                                    initialExpensePrefill = prefill
                                )
                            )
                        }
                    }
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
                                        recurringTransactionService.ensureRecurringTransactionsGeneratedThroughDefaultWindow()
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
                                        recurringTransactionService.ensureRecurringTransactionsGeneratedThroughDefaultWindow()
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
