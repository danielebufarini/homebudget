package it.homebudget.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.data.importExpensesFromCsv
import it.homebudget.app.localization.LocalStrings
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
internal actual fun rememberCsvImportLauncher(
    onImportMessage: (String) -> Unit
): () -> Unit {
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
            val message = runCatching {
                val csvText = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.readBytes().decodeToString()
                } ?: error(strings.csvImportFailed)

                val result = importExpensesFromCsv(
                    repository = repository,
                    csvText = csvText
                )

                if (result.importedCount == 0 && result.skippedCount == 0) {
                    strings.csvImportNoRows
                } else {
                    strings.csvImportSuccess(
                        importedCount = result.importedCount,
                        skippedCount = result.skippedCount
                    )
                }
            }.getOrElse {
                strings.csvImportFailed
            }

            onImportMessage(message)
        }
    }

    return remember(launcher) {
        {
            launcher.launch(arrayOf("text/*", "text/csv", "application/csv", "application/vnd.ms-excel"))
        }
    }
}
