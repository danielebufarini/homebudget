package it.danielebufarini.spesify.ui.screens.platform

import androidx.compose.runtime.Composable

internal expect class CsvImportLauncher {
    fun open()

    @Composable
    fun Render()
}

@Composable
internal expect fun rememberCsvImportLauncher(
    onImportMessage: (String) -> Unit
): CsvImportLauncher
