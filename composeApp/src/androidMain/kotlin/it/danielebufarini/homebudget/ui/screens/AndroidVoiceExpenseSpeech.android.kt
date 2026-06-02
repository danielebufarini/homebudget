package it.danielebufarini.homebudget.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.util.Locale

// SpeechRecognizer lifecycle, permission handling, and transcript capture for Android voice input.

internal class AndroidVoiceExpenseSpeechController(
    val isRecognizerAvailable: Boolean,
    private val startListeningAction: () -> Unit,
    private val stopListeningAction: () -> Unit,
    private val cancelAction: () -> Unit
) {
    fun startListening() = startListeningAction()

    fun stopListening() = stopListeningAction()

    fun cancel() = cancelAction()
}

@Composable
internal fun rememberAndroidVoiceExpenseSpeechController(
    showDialog: Boolean,
    uiStrings: AndroidVoiceExpenseUiStrings,
    isListening: Boolean,
    transcript: String,
    onPrepareListening: () -> Unit,
    onTranscriptChange: (String) -> Unit,
    onRecognizedSpeech: (String) -> Unit,
    onStatusMessageChange: (String?) -> Unit,
    onListeningChange: (Boolean) -> Unit
): AndroidVoiceExpenseSpeechController {
    val context = LocalContext.current
    val currentIsListening by rememberUpdatedState(isListening)
    val currentTranscript by rememberUpdatedState(transcript)
    val currentPrepareListening by rememberUpdatedState(onPrepareListening)
    val currentTranscriptChange by rememberUpdatedState(onTranscriptChange)
    val currentRecognizedSpeech by rememberUpdatedState(onRecognizedSpeech)
    val currentStatusMessageChange by rememberUpdatedState(onStatusMessageChange)
    val currentListeningChange by rememberUpdatedState(onListeningChange)

    var pendingSpeechStart by remember { mutableStateOf(false) }

    val speechRecognizer = remember(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }
    }

    fun buildSpeechRecognizerIntent(): Intent {
        val languageTag = Locale.getDefault().toLanguageTag()
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 4_000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1_500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2_000L)
            putExtra(RecognizerIntent.EXTRA_PROMPT, uiStrings.voiceExpensePrompt)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
    }

    fun beginSpeechRecognition() {
        val recognizer = speechRecognizer
        if (recognizer == null) {
            currentStatusMessageChange(uiStrings.voiceExpenseStatusNoRecognizer)
            return
        }

        currentPrepareListening()
        currentListeningChange(true)
        currentStatusMessageChange(uiStrings.voiceExpenseStatusListening)

        runCatching {
            recognizer.cancel()
            recognizer.startListening(buildSpeechRecognizerIntent())
        }.onFailure { error ->
            currentListeningChange(false)
            currentStatusMessageChange(error.message ?: uiStrings.voiceExpenseStatusUnableToStart)
        }
    }

    fun stopSpeechRecognition() {
        if (!currentIsListening) {
            return
        }
        currentListeningChange(false)
        currentStatusMessageChange(uiStrings.voiceExpenseStatusProcessing)
        runCatching {
            speechRecognizer?.stopListening()
        }.onFailure { error ->
            currentStatusMessageChange(error.message ?: uiStrings.voiceExpenseStatusUnableToStop)
        }
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingSpeechStart = true
        } else {
            currentListeningChange(false)
            pendingSpeechStart = false
            currentStatusMessageChange(uiStrings.voiceExpenseStatusMicrophonePermissionRequired)
        }
    }

    fun startSpeechRecognition() {
        if (speechRecognizer == null) {
            currentStatusMessageChange(uiStrings.voiceExpenseStatusNoRecognizer)
            return
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            beginSpeechRecognition()
        } else {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun cancelSpeechRecognition() {
        currentListeningChange(false)
        pendingSpeechStart = false
        speechRecognizer?.cancel()
    }

    LaunchedEffect(pendingSpeechStart, showDialog) {
        if (pendingSpeechStart && showDialog) {
            pendingSpeechStart = false
            beginSpeechRecognition()
        }
    }

    DisposableEffect(speechRecognizer) {
        if (speechRecognizer == null) {
            onDispose { }
        } else {
            val listener = object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    currentStatusMessageChange(uiStrings.voiceExpenseStatusListening)
                }

                override fun onBeginningOfSpeech() {
                    currentStatusMessageChange(uiStrings.voiceExpenseStatusListening)
                }

                override fun onRmsChanged(rmsdB: Float) = Unit

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    currentListeningChange(false)
                    currentStatusMessageChange(uiStrings.voiceExpenseStatusProcessing)
                }

                override fun onError(error: Int) {
                    currentListeningChange(false)
                    if (currentTranscript.isNotBlank()) {
                        currentRecognizedSpeech(currentTranscript)
                        return
                    }
                    currentStatusMessageChange(
                        when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> uiStrings.voiceExpenseStatusNotUnderstood
                            SpeechRecognizer.ERROR_AUDIO -> uiStrings.voiceExpenseStatusAudioUnavailable
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> uiStrings.voiceExpenseStatusMicrophonePermissionRequired
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> uiStrings.voiceExpenseStatusRecognizerBusy
                            SpeechRecognizer.ERROR_NETWORK,
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                            SpeechRecognizer.ERROR_SERVER -> uiStrings.voiceExpenseStatusServiceUnavailable
                            SpeechRecognizer.ERROR_CLIENT -> uiStrings.voiceExpenseStatusCancelled
                            else -> uiStrings.voiceExpenseStatusUnableToCapture
                        }
                    )
                }

                override fun onResults(results: Bundle?) {
                    currentListeningChange(false)
                    val spokenText = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()

                    if (spokenText.isBlank()) {
                        currentStatusMessageChange(uiStrings.voiceExpenseStatusNoSpeech)
                        return
                    }

                    currentRecognizedSpeech(spokenText)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partialText = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    if (partialText.isNotBlank()) {
                        currentTranscriptChange(partialText)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            }

            speechRecognizer.setRecognitionListener(listener)
            onDispose {
                speechRecognizer.cancel()
                speechRecognizer.destroy()
            }
        }
    }

    return remember(speechRecognizer) {
        AndroidVoiceExpenseSpeechController(
            isRecognizerAvailable = speechRecognizer != null,
            startListeningAction = ::startSpeechRecognition,
            stopListeningAction = ::stopSpeechRecognition,
            cancelAction = ::cancelSpeechRecognition
        )
    }
}
