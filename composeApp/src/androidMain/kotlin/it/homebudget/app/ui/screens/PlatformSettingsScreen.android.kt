package it.homebudget.app.ui.screens

import android.app.Activity
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.auto_backup_description
import homebudget.composeapp.generated.resources.cloud_backup_authorized
import homebudget.composeapp.generated.resources.cloud_backup_checking
import homebudget.composeapp.generated.resources.cloud_backup_disabled
import homebudget.composeapp.generated.resources.cloud_backup_drive_toggle
import homebudget.composeapp.generated.resources.cloud_backup_drive_toggle_description
import homebudget.composeapp.generated.resources.cloud_backup_enabling
import homebudget.composeapp.generated.resources.cloud_backup_google_setup_required
import homebudget.composeapp.generated.resources.cloud_backup_requires_consent
import homebudget.composeapp.generated.resources.cloud_backup_settings_title
import homebudget.composeapp.generated.resources.cloud_backup_toggle_cancelled
import homebudget.composeapp.generated.resources.cloud_backup_unavailable
import homebudget.composeapp.generated.resources.google_drive_access_failed
import homebudget.composeapp.generated.resources.settings
import it.homebudget.app.data.AndroidCloudBackupStore
import it.homebudget.app.data.CloudSyncService
import it.homebudget.app.data.DriveAuthorizationResult
import it.homebudget.app.data.DriveAuthorizationSnapshot
import it.homebudget.app.data.DriveAuthorizationState
import it.homebudget.app.data.GoogleDriveAuthorizationManager
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

actual fun platformSettingsScreen(): Screen = AndroidSettingsScreen()

private class AndroidSettingsScreen : Screen {
    @Composable
    override fun Content() {
        AndroidSettingsRoute()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AndroidSettingsRoute() {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val activity = context.findActivity()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val authorizationManager: GoogleDriveAuthorizationManager = koinInject()
    val cloudSyncService: CloudSyncService = koinInject()
    val cloudBackupStore: AndroidCloudBackupStore = koinInject()

    var authorizationSnapshot by remember { mutableStateOf<DriveAuthorizationSnapshot?>(null) }
    var isBusy by remember { mutableStateOf(false) }

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

    val enableCancelledMessage = stringResource(Res.string.cloud_backup_toggle_cancelled)
    val setupRequiredMessage = stringResource(Res.string.cloud_backup_google_setup_required)
    val unavailableMessage = stringResource(Res.string.cloud_backup_unavailable)
    val authorizeFailureMessage = stringResource(Res.string.google_drive_access_failed)

    val authorizationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        scope.launch {
            isBusy = true
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
                        val message = when {
                            explicitResult is DriveAuthorizationResult.Failed &&
                                !explicitResult.message.isNullOrBlank() -> explicitResult.message
                            !authorizationManager.hasWebClientIdConfigured() -> setupRequiredMessage
                            result.resultCode == Activity.RESULT_CANCELED -> enableCancelledMessage
                            else -> authorizeFailureMessage
                        }
                        snackbarHostState.showSnackbar(message)
                    }
                }
            }
            isBusy = false
        }
    }

    val title = stringResource(Res.string.settings)
    val sectionTitle = stringResource(Res.string.cloud_backup_settings_title)
    val description = stringResource(Res.string.auto_backup_description)
    val toggleLabel = stringResource(Res.string.cloud_backup_drive_toggle)
    val toggleDescription = stringResource(Res.string.cloud_backup_drive_toggle_description)
    val checkingMessage = stringResource(Res.string.cloud_backup_checking)
    val enablingMessage = stringResource(Res.string.cloud_backup_enabling)
    val disabledMessage = stringResource(Res.string.cloud_backup_disabled)
    val consentMessage = stringResource(Res.string.cloud_backup_requires_consent)
    val authorizedMessage = stringResource(Res.string.cloud_backup_authorized)

    val statusMessage = when {
        isBusy -> enablingMessage
        authorizationSnapshot == null -> checkingMessage
        authorizationSnapshot?.state == DriveAuthorizationState.Unavailable -> unavailableMessage
        authorizationSnapshot?.state == DriveAuthorizationState.Authorized -> authorizedMessage
        authorizationSnapshot?.isDriveBackupEnabled == true -> consentMessage
        else -> disabledMessage
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { navigator?.pop() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = title
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = sectionTitle,
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            ListItem(
                headlineContent = { Text(toggleLabel) },
                supportingContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(toggleDescription)
                        Text(
                            text = statusMessage,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                trailingContent = {
                    Switch(
                        checked = authorizationSnapshot?.isDriveBackupEnabled == true,
                        enabled = !isBusy && activity != null,
                        onCheckedChange = { enabled ->
                            val hostActivity = activity ?: return@Switch
                            scope.launch {
                                isBusy = true
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
                                        snackbarHostState.showSnackbar(
                                            authorizationResult.message ?: authorizeFailureMessage
                                        )
                                        refreshSnapshot()
                                        isBusy = false
                                    }
                                }
                            }
                        }
                    )
                }
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is android.content.ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
