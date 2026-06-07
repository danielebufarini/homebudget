package it.danielebufarini.spesify.ui.screens.platform

import android.app.Activity
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import it.danielebufarini.spesify.data.AndroidCloudBackupStore
import it.danielebufarini.spesify.data.CloudSyncService
import it.danielebufarini.spesify.data.DriveAuthorizationResult
import it.danielebufarini.spesify.data.DriveAuthorizationSnapshot
import it.danielebufarini.spesify.data.DriveAuthorizationState
import it.danielebufarini.spesify.data.GoogleDriveAuthorizationManager
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import spesify.composeapp.generated.resources.Res
import spesify.composeapp.generated.resources.auto_backup_description
import spesify.composeapp.generated.resources.auto_backup_note
import spesify.composeapp.generated.resources.cloud_backup_authorized
import spesify.composeapp.generated.resources.cloud_backup_checking
import spesify.composeapp.generated.resources.cloud_backup_drive_toggle
import spesify.composeapp.generated.resources.cloud_backup_enabling
import spesify.composeapp.generated.resources.cloud_backup_google_setup_required
import spesify.composeapp.generated.resources.cloud_backup_requires_consent
import spesify.composeapp.generated.resources.cloud_backup_toggle_cancelled
import spesify.composeapp.generated.resources.cloud_backup_unavailable
import spesify.composeapp.generated.resources.google_drive_access_failed

@Composable
internal actual fun PlatformCloudBackupDrawerSection() {
    val context = LocalContext.current
    val activity = context.findActivity()
    val scope = rememberCoroutineScope()
    val authorizationManager: GoogleDriveAuthorizationManager = koinInject()
    val cloudSyncService: CloudSyncService = koinInject()
    val cloudBackupStore: AndroidCloudBackupStore = koinInject()

    var authorizationSnapshot by remember { mutableStateOf<DriveAuthorizationSnapshot?>(null) }
    var isBusy by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }

    suspend fun refreshSnapshot() {
        authorizationSnapshot = authorizationManager.checkDriveAuthorizationStatus()
    }

    suspend fun runImmediateCloudSync() {
        val backup = cloudSyncService.buildBackupFile()
        cloudBackupStore.writeBackupFile(backup)
    }

    LaunchedEffect(Unit) {
        refreshSnapshot()
    }

    val toggleLabel = stringResource(Res.string.cloud_backup_drive_toggle)
    val description = stringResource(Res.string.auto_backup_description)
    val note = stringResource(Res.string.auto_backup_note)
    val checkingMessage = stringResource(Res.string.cloud_backup_checking)
    val enablingMessage = stringResource(Res.string.cloud_backup_enabling)
    val consentMessage = stringResource(Res.string.cloud_backup_requires_consent)
    val authorizedMessage = stringResource(Res.string.cloud_backup_authorized)
    val unavailableMessage = stringResource(Res.string.cloud_backup_unavailable)
    val cancelledMessage = stringResource(Res.string.cloud_backup_toggle_cancelled)
    val setupRequiredMessage = stringResource(Res.string.cloud_backup_google_setup_required)
    val authorizeFailureMessage = stringResource(Res.string.google_drive_access_failed)

    val statusMessage = when {
        isBusy -> enablingMessage
        authorizationSnapshot == null -> checkingMessage
        authorizationSnapshot?.state == DriveAuthorizationState.Unavailable -> unavailableMessage
        authorizationSnapshot?.state == DriveAuthorizationState.Authorized -> authorizedMessage
        authorizationSnapshot?.isDriveBackupEnabled == true -> consentMessage
        else -> consentMessage
    }

    val authorizationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        scope.launch {
            isBusy = true
            feedbackMessage = null
            val explicitResult = result.data?.let { authorizationManager.completeManualAuthorization(it) }
            when (explicitResult) {
                DriveAuthorizationResult.Authorized -> {
                    runCatching { runImmediateCloudSync() }
                    refreshSnapshot()
                }
                is DriveAuthorizationResult.Failed,
                is DriveAuthorizationResult.NeedsResolution,
                null -> {
                    refreshSnapshot()
                    if (authorizationSnapshot?.state == DriveAuthorizationState.Authorized) {
                        runCatching { runImmediateCloudSync() }
                    } else {
                        authorizationManager.setDriveBackupEnabled(false)
                        feedbackMessage = when {
                            explicitResult is DriveAuthorizationResult.Failed &&
                                !explicitResult.message.isNullOrBlank() -> explicitResult.message
                            !authorizationManager.hasWebClientIdConfigured() -> setupRequiredMessage
                            result.resultCode == Activity.RESULT_CANCELED -> cancelledMessage
                            else -> authorizeFailureMessage
                        }
                    }
                }
            }
            isBusy = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        DrawerToggleRow(
            label = toggleLabel,
            checked = authorizationSnapshot?.isDriveBackupEnabled == true,
            enabled = !isBusy && activity != null,
            onCheckedChange = { enabled ->
                val hostActivity = activity ?: return@DrawerToggleRow
                scope.launch {
                    isBusy = true
                    feedbackMessage = null
                    if (!enabled) {
                        authorizationManager.setDriveBackupEnabled(false)
                        refreshSnapshot()
                        isBusy = false
                        return@launch
                    }

                    when (val authorizationResult = authorizationManager.beginManualAuthorization(hostActivity)) {
                        DriveAuthorizationResult.Authorized -> {
                            runCatching { runImmediateCloudSync() }
                            refreshSnapshot()
                            isBusy = false
                        }
                        is DriveAuthorizationResult.NeedsResolution -> {
                            authorizationLauncher.launch(authorizationResult.request)
                        }
                        is DriveAuthorizationResult.Failed -> {
                            authorizationManager.setDriveBackupEnabled(false)
                            feedbackMessage = authorizationResult.message ?: authorizeFailureMessage
                            refreshSnapshot()
                            isBusy = false
                        }
                    }
                }
            }
        )

        if (isBusy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = feedbackMessage ?: statusMessage,
            style = MaterialTheme.typography.bodySmall,
            color = if (feedbackMessage == null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            }
        )
    }
}

@Composable
private fun DrawerToggleRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).padding(end = 12.dp)
        )
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is android.content.ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
