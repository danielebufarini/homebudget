package it.homebudget.app.ui.screens

import androidx.compose.runtime.Composable

internal expect class BackupRestoreLauncher {
    fun open()

    @Composable
    fun Render()
}

@Composable
internal expect fun rememberBackupRestoreLauncher(
    onRestoreMessage: (String) -> Unit
): BackupRestoreLauncher
