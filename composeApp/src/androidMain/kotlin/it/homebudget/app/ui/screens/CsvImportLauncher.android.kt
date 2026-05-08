package it.homebudget.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.csv_import_failed
import homebudget.composeapp.generated.resources.csv_import_no_rows
import homebudget.composeapp.generated.resources.csv_import_success
import homebudget.composeapp.generated.resources.csv_import_success_with_skipped
import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.data.csv.MAX_CSV_IMPORT_BYTES
import it.homebudget.app.data.csv.importBudgetItemsFromCsv
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import java.io.ByteArrayOutputStream
import java.io.InputStream

internal actual class CsvImportLauncher(
    private val onOpen: () -> Unit,
    private val renderContent: @Composable () -> Unit
) {
    actual fun open() {
        onOpen()
    }

    @Composable
    actual fun Render() {
        renderContent()
    }
}

@Composable
internal actual fun rememberCsvImportLauncher(
    onImportMessage: (String) -> Unit
): CsvImportLauncher {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository: ExpenseRepository = koinInject()
    val csvImportFailedLabel = stringResource(Res.string.csv_import_failed)
    val csvImportNoRowsLabel = stringResource(Res.string.csv_import_no_rows)
    val csvImportSuccessLabel = stringResource(Res.string.csv_import_success)
    val csvImportSuccessWithSkippedLabel = stringResource(Res.string.csv_import_success_with_skipped)

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }

        scope.launch {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.readUtf8TextWithLimit(MAX_CSV_IMPORT_BYTES)
                } ?: error(csvImportFailedLabel)
            }.onSuccess { csvText ->
                val result = importBudgetItemsFromCsv(
                    repository = repository,
                    csvText = csvText
                )

                onImportMessage(
                    if (result.importedCount == 0 && result.skippedCount == 0) {
                        csvImportNoRowsLabel
                    } else {
                        if (result.skippedCount == 0) {
                            csvImportSuccessLabel
                                .replace("%1\$d", result.importedCount.toString())
                        } else {
                            csvImportSuccessWithSkippedLabel
                                .replace("%1\$d", result.importedCount.toString())
                                .replace("%2\$d", result.skippedCount.toString())
                        }
                    }
                )
            }.onFailure {
                onImportMessage(csvImportFailedLabel)
            }
        }
    }

    return remember(launcher) {
        CsvImportLauncher(
            onOpen = {
                launcher.launch(arrayOf("text/*", "text/csv", "application/csv"))
            },
            renderContent = {}
        )
    }
}


private fun InputStream.readUtf8TextWithLimit(maxBytes: Int): String {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var totalBytes = 0

    while (true) {
        val read = read(buffer)
        if (read == -1) break
        totalBytes += read
        require(totalBytes <= maxBytes) { "CSV import file is too large." }
        output.write(buffer, 0, read)
    }

    return output.toByteArray().decodeToString()
}
