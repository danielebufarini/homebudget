package it.homebudget.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.localization.rememberCategoryNameResolver
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
internal actual fun DashboardVoiceExpenseAction(openVoiceExpenseRequest: Int, modifier: Modifier) {

    val repository: ExpenseRepository = koinInject()
    val scope = rememberCoroutineScope()
    val generativeModel = remember { Generation.getClient() }
    val resolveCategoryName = rememberCategoryNameResolver()
    val uiStrings = rememberAndroidVoiceExpenseUiStrings()

    var availability by remember { mutableStateOf<Int?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var transcript by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf<AndroidVoiceExpenseDraft?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isCheckingAvailability by remember { mutableStateOf(false) }
    var isParsing by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }

    fun resetDialogState() {
        transcript = ""
        draft = null
        statusMessage = null
        isCheckingAvailability = false
        isParsing = false
        isSaving = false
        isDownloading = false
        isListening = false
    }

    suspend fun refreshAvailability() {
        isCheckingAvailability = true
        val result = checkAndroidVoiceExpenseAvailability(
            generativeModel = generativeModel,
            uiStrings = uiStrings
        )
        availability = result.status
        result.errorMessage?.let { statusMessage = it }
        isCheckingAvailability = false
    }

    fun handleRecognizedSpeech(spokenText: String) {
        transcript = spokenText
        draft = null
        statusMessage = uiStrings.voiceExpenseStatusInterpreting

        scope.launch {
            isParsing = true
            val draftBuildResult = buildAndroidVoiceExpenseDraft(
                transcript = spokenText,
                repository = repository,
                resolveCategoryName = { category ->
                    resolveCategoryName(category.id, category.name)
                },
                generativeModel = generativeModel,
                availability = availability,
                uiStrings = uiStrings
            )
            draft = draftBuildResult.draft
            statusMessage = draftBuildResult.statusMessage
            isParsing = false
        }
    }

    val speechController = rememberAndroidVoiceExpenseSpeechController(
        showDialog = showDialog,
        uiStrings = uiStrings,
        isListening = isListening,
        transcript = transcript,
        onPrepareListening = {
            transcript = ""
            draft = null
        },
        onTranscriptChange = { transcript = it },
        onRecognizedSpeech = ::handleRecognizedSpeech,
        onStatusMessageChange = { statusMessage = it },
        onListeningChange = { isListening = it }
    )

    LaunchedEffect(Unit) {
        refreshAvailability()
    }

    DisposableEffect(generativeModel) {
        onDispose {
            generativeModel.close()
        }
    }

    fun dismissDialog() {
        speechController.cancel()
        showDialog = false
    }

    fun openVoiceExpenseDialogAndStartListening() {
        resetDialogState()
        showDialog = true
        scope.launch {
            refreshAvailability()
            when (availability) {
                FeatureStatus.AVAILABLE -> speechController.startListening()
                FeatureStatus.UNAVAILABLE -> {
                    statusMessage = uiStrings.voiceExpenseStatusFallbackParser
                    speechController.startListening()
                }
                FeatureStatus.DOWNLOADABLE -> {
                    statusMessage = uiStrings.voiceExpenseStatusDownloadingAvailable
                }
                FeatureStatus.DOWNLOADING -> {
                    statusMessage = uiStrings.voiceExpenseStatusDownloadingInProgress
                }
                else -> {
                    statusMessage = uiStrings.voiceExpenseStatusCheckingAvailability
                }
            }
        }
    }

    LaunchedEffect(openVoiceExpenseRequest) {
        if (openVoiceExpenseRequest > 0) {
            openVoiceExpenseDialogAndStartListening()
        }
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .clickable { openVoiceExpenseDialogAndStartListening() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = uiStrings.voiceExpense
        )
    }

    if (!showDialog) {
        return
    }

    AlertDialog(
        onDismissRequest = { dismissDialog() },
        title = {
            Text(uiStrings.voiceExpense)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                statusMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (statusMessage == null) {
                    Text(
                        text = buildAndroidVoiceAvailabilitySummary(availability, uiStrings),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (isCheckingAvailability || isParsing || isSaving || isDownloading || isListening) {
                    CircularProgressIndicator()
                }

                if (transcript.isNotBlank()) {
                    VoiceExpenseSection(
                        title = uiStrings.transcript,
                        body = transcript
                    )
                }

                draft?.let { currentDraft ->
                    VoiceExpenseSection(
                        title = if (currentDraft.action == AndroidVoiceExpenseActionKind.Update) {
                            uiStrings.update
                        } else {
                            uiStrings.newExpense
                        },
                        body = buildAndroidVoiceExpenseSummary(currentDraft, uiStrings)
                    )
                }

                if (transcript.isBlank() && draft == null && statusMessage == null) {
                    Text(
                        text = uiStrings.voiceExpenseEmptyPrompt,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            when {
                draft != null -> {
                    Button(
                        enabled = !isSaving && !isParsing,
                        onClick = {
                            val currentDraft = draft ?: return@Button
                            scope.launch {
                                isSaving = true
                                val draftCommitResult = commitAndroidVoiceExpenseDraft(
                                    draft = currentDraft,
                                    repository = repository,
                                    uiStrings = uiStrings
                                )
                                if (draftCommitResult.success) {
                                    showDialog = false
                                } else {
                                    statusMessage = draftCommitResult.errorMessage
                                }
                                isSaving = false
                            }
                        }
                    ) {
                        Text(
                            if (draft?.action == AndroidVoiceExpenseActionKind.Update) {
                                uiStrings.updateExpense
                            } else {
                                uiStrings.saveExpense
                            }
                        )
                    }
                }
                availability == FeatureStatus.DOWNLOADABLE -> {
                    Button(
                        enabled = !isDownloading,
                        onClick = {
                            scope.launch {
                                isDownloading = true
                                statusMessage = uiStrings.voiceExpenseModelDownloading
                                runCatching {
                                    generativeModel.download().collect()
                                    refreshAvailability()
                                }.onSuccess {
                                    if (availability == FeatureStatus.AVAILABLE) {
                                        statusMessage = uiStrings.voiceExpenseModelReady
                                        speechController.startListening()
                                    } else {
                                        statusMessage = uiStrings.voiceExpenseModelNotReady
                                    }
                                }.onFailure { error ->
                                    statusMessage = error.message ?: uiStrings.voiceExpenseStatusUnableToDownloadModel
                                }
                                isDownloading = false
                            }
                        }
                    ) {
                        Text(uiStrings.voiceExpenseActionDownloadModel)
                    }
                }
                availability == FeatureStatus.AVAILABLE || availability == FeatureStatus.UNAVAILABLE -> {
                    Button(
                        enabled = !isCheckingAvailability && !isParsing,
                        onClick = {
                            if (isListening) {
                                speechController.stopListening()
                            } else {
                                speechController.startListening()
                            }
                        }
                    ) {
                        Text(
                            when {
                                isListening -> uiStrings.voiceExpenseActionStopListening
                                transcript.isBlank() -> uiStrings.voiceExpenseActionStartListening
                                else -> uiStrings.voiceExpenseActionListenAgain
                            }
                        )
                    }
                }
                else -> {
                    Button(
                        enabled = !isCheckingAvailability && !isDownloading,
                        onClick = {
                            scope.launch {
                                refreshAvailability()
                            }
                        }
                    ) {
                        Text(uiStrings.refresh)
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = { dismissDialog() }
            ) {
                Text(uiStrings.close)
            }
        }
    )
}

@Composable
private fun VoiceExpenseSection(
    title: String,
    body: String
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
