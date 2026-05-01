package it.homebudget.app.ui.screens

import androidx.compose.runtime.Composable

internal actual class CsvExportLauncher {
    actual fun open() = Unit

    @Composable
    actual fun Render() = Unit
}

@Composable
internal actual fun rememberCsvExportLauncher(
    onExportMessage: (String) -> Unit
): CsvExportLauncher = CsvExportLauncher()
