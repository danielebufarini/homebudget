package it.homebudget.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.data.importBudgetItemsFromCsv
import it.homebudget.app.localization.LocalStrings
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

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
    val strings = LocalStrings.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }

        scope.launch {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.readBytes().decodeToString()
                } ?: error(strings.csvImportFailed)
            }.onSuccess { csvText ->
                val result = importBudgetItemsFromCsv(
                    repository = repository,
                    csvText = csvText
                )

                onImportMessage(
                    if (result.importedCount == 0 && result.skippedCount == 0) {
                        strings.csvImportNoRows
                    } else {
                        strings.csvImportSuccess(
                            importedCount = result.importedCount,
                            skippedCount = result.skippedCount
                        )
                    }
                )
            }.onFailure {
                onImportMessage(strings.csvImportFailed)
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
