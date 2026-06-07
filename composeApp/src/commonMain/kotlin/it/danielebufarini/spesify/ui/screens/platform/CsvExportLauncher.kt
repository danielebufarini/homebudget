package it.danielebufarini.spesify.ui.screens.platform

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
