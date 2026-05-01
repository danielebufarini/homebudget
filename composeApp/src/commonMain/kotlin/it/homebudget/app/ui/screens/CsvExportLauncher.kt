package it.homebudget.app.ui.screens

import androidx.compose.runtime.Composable

internal expect class CsvExportLauncher {
    fun open()

    @Composable
    fun Render()
}

@Composable
internal expect fun rememberCsvExportLauncher(
    onExportMessage: (String) -> Unit
): CsvExportLauncher
