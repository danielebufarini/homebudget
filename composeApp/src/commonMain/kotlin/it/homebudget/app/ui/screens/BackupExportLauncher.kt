package it.homebudget.app.ui.screens

import androidx.compose.runtime.Composable

internal expect class BackupExportLauncher {
    fun open()

    @Composable
    fun Render()
}

@Composable
internal expect fun rememberBackupExportLauncher(
    onExportMessage: (String) -> Unit
): BackupExportLauncher
