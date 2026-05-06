package it.homebudget.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.cancel
import homebudget.composeapp.generated.resources.csv_transfer_description
import homebudget.composeapp.generated.resources.csv_transfer_note
import homebudget.composeapp.generated.resources.csv_transfer_title
import homebudget.composeapp.generated.resources.export_csv_ellipsis
import homebudget.composeapp.generated.resources.import_csv
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Stable
internal class AndroidDataTransferSheetState {
    var showCsvTransferSheet by mutableStateOf(false)

    fun openCsvTransferSheet() {
        showCsvTransferSheet = true
    }

    fun dismissCsvTransferSheet() {
        showCsvTransferSheet = false
    }
}

@Composable
internal fun rememberAndroidDataTransferSheetState(): AndroidDataTransferSheetState {
    return remember { AndroidDataTransferSheetState() }
}

@Composable
internal fun AndroidDataTransferUi(
    snackbarHostState: SnackbarHostState,
    state: AndroidDataTransferSheetState
) {
    val scope = rememberCoroutineScope()
    val importCsvLauncher = rememberCsvImportLauncher { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }
    val exportCsvLauncher = rememberCsvExportLauncher { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    importCsvLauncher.Render()
    exportCsvLauncher.Render()

    if (state.showCsvTransferSheet) {
        ActionSheet(
            title = stringResource(Res.string.csv_transfer_title),
            description = stringResource(Res.string.csv_transfer_description),
            note = stringResource(Res.string.csv_transfer_note),
            onDismiss = state::dismissCsvTransferSheet,
            primaryLabel = stringResource(Res.string.export_csv_ellipsis),
            primaryAction = {
                state.dismissCsvTransferSheet()
                exportCsvLauncher.open()
            },
            secondaryLabel = stringResource(Res.string.import_csv),
            secondaryAction = {
                state.dismissCsvTransferSheet()
                importCsvLauncher.open()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionSheet(
    title: String,
    description: String,
    note: String,
    onDismiss: () -> Unit,
    primaryLabel: String,
    primaryAction: () -> Unit,
    secondaryLabel: String,
    secondaryAction: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = primaryAction,
                modifier = Modifier.fillMaxWidth(),
                colors = homeBudgetButtonColors()
            ) {
                Text(primaryLabel)
            }

            Button(
                onClick = secondaryAction,
                modifier = Modifier.fillMaxWidth(),
                colors = homeBudgetFilledTonalButtonColors()
            ) {
                Text(secondaryLabel)
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = homeBudgetTextButtonColors()
            ) {
                Text(stringResource(Res.string.cancel))
            }
        }
    }
}
