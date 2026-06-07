package it.danielebufarini.spesify.ui.screens.platform

import androidx.compose.runtime.Composable

internal actual class CsvImportLauncher {
    actual fun open() = Unit

    @Composable
    actual fun Render() = Unit
}

@Composable
internal actual fun rememberCsvImportLauncher(
    onImportMessage: (String) -> Unit
): CsvImportLauncher = CsvImportLauncher()
