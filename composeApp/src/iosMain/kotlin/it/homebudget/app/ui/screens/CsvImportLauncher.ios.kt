package it.homebudget.app.ui.screens

import androidx.compose.runtime.Composable

@Composable
internal actual fun rememberCsvImportLauncher(
    onImportMessage: (String) -> Unit
): () -> Unit = {}
