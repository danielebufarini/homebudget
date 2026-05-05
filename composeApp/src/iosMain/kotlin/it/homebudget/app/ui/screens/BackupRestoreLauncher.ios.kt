package it.homebudget.app.ui.screens

import androidx.compose.runtime.Composable

internal actual class BackupRestoreLauncher {
    actual fun open() = Unit

    @Composable
    actual fun Render() = Unit
}

@Composable
internal actual fun rememberBackupRestoreLauncher(
    onRestoreMessage: (String) -> Unit
): BackupRestoreLauncher = BackupRestoreLauncher()
