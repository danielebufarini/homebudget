package it.homebudget.app.ui.screens

import androidx.compose.runtime.Composable

internal actual class BackupExportLauncher {
    actual fun open() = Unit

    @Composable
    actual fun Render() = Unit
}

@Composable
internal actual fun rememberBackupExportLauncher(
    onExportMessage: (String) -> Unit
): BackupExportLauncher = BackupExportLauncher()
