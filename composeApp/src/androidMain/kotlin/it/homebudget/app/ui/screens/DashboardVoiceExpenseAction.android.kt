package it.homebudget.app.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.data.PendingExpense
import it.homebudget.app.data.formatAmountInput
import it.homebudget.app.data.parseAmountInput
import it.homebudget.app.database.Category
import it.homebudget.app.database.Expense
import it.homebudget.app.localization.LocalStrings
import it.homebudget.app.localization.Strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.json.JSONObject
import org.koin.compose.koinInject
import java.text.Normalizer
import java.util.*
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Instant

private data class AndroidVoiceExpenseCategory(
    val id: String,
    val name: String
)

private data class AndroidVoiceExpenseCandidate(
    val id: String,
    val amountInput: String,
    val categoryId: String,
    val categoryName: String,
    val description: String?,
    val date: Long,
    val isShared: Boolean
)

private data class AndroidVoiceExpenseSnapshot(
    val categories: List<AndroidVoiceExpenseCategory>,
    val recentExpenses: List<AndroidVoiceExpenseCandidate>
)

private enum class AndroidVoiceExpenseActionKind {
    Create,
    Update,
    Ignore
}

private data class AndroidVoiceExpenseInterpretation(
    val action: AndroidVoiceExpenseActionKind,
    val targetExpenseId: String?,
    val amountInput: String?,
    val categoryName: String?,
    val description: String?,
    val date: LocalDate?,
    val isShared: Boolean
)

private data class AndroidVoiceExpenseDraft(
    val action: AndroidVoiceExpenseActionKind,
    val expenseId: String?,
    val amountInput: String,
    val categoryId: String,
    val categoryName: String,
    val description: String?,
    val date: Long,
    val isShared: Boolean
)

@Composable
internal actual fun DashboardVoiceExpenseAction() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        return
    }

    val repository: ExpenseRepository = koinInject()
    val scope = rememberCoroutineScope()
    val generativeModel = remember { Generation.getClient() }
    val context = LocalContext.current
    val strings = LocalStrings.current

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
    var pendingSpeechStart by remember { mutableStateOf(false) }

    val speechRecognizer = remember(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }
    }

    fun resetDialogState() {
        transcript = ""
        draft = null
        statusMessage = null
        isCheckingAvailability = false
        isParsing = false
        isSaving = false
        isDownloading = false
        isListening = false
        pendingSpeechStart = false
    }

    suspend fun refreshAvailability() {
        isCheckingAvailability = true
        availability = runCatching {
            generativeModel.checkStatus()
        }.getOrElse {
            statusMessage = it.message ?: "Unable to check Gemini Nano availability."
            FeatureStatus.UNAVAILABLE
        }
        isCheckingAvailability = false
    }

    fun handleRecognizedSpeech(spokenText: String) {
        transcript = spokenText
        draft = null
        statusMessage = "Interpreting voice input..."

        scope.launch {
            isParsing = true
            val parseResult = runCatching {
                val snapshot = loadAndroidVoiceExpenseSnapshot(repository, strings)
                val interpretation = interpretAndroidVoiceExpense(
                    transcript = spokenText,
                    snapshot = snapshot,
                    generativeModel = generativeModel,
                    availability = availability
                )
                resolveAndroidVoiceExpenseDraft(
                    interpretation = interpretation,
                    snapshot = snapshot,
                    transcript = spokenText
                )
            }

            parseResult.onSuccess { resolvedDraft ->
                draft = resolvedDraft
                statusMessage = when {
                    resolvedDraft == null -> "The voice command did not describe a usable expense."
                    resolvedDraft.action == AndroidVoiceExpenseActionKind.Update ->
                        "Ready to update the matched expense."
                    else -> "Ready to save a new expense."
                }
            }.onFailure { error ->
                draft = null
                statusMessage = error.message ?: "Unable to interpret the voice command."
            }
            isParsing = false
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
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak an expense command")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
    }

    fun beginSpeechRecognition() {
        val recognizer = speechRecognizer
        if (recognizer == null) {
            statusMessage = "No speech recognizer is available on this device."
            return
        }

        transcript = ""
        draft = null
        isListening = true
        statusMessage = "Listening..."

        runCatching {
            recognizer.cancel()
            recognizer.startListening(buildSpeechRecognizerIntent())
        }.onFailure { error ->
            isListening = false
            statusMessage = error.message ?: "Unable to start speech recognition."
        }
    }

    fun stopSpeechRecognition() {
        if (!isListening) {
            return
        }
        isListening = false
        statusMessage = "Processing speech..."
        runCatching {
            speechRecognizer?.stopListening()
        }.onFailure { error ->
            statusMessage = error.message ?: "Unable to stop speech recognition."
        }
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingSpeechStart = true
        } else {
            isListening = false
            pendingSpeechStart = false
            statusMessage = "Microphone permission is required."
        }
    }

    fun startSpeechRecognition() {
        if (speechRecognizer == null) {
            statusMessage = "No speech recognizer is available on this device."
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
                    statusMessage = "Listening..."
                }

                override fun onBeginningOfSpeech() {
                    statusMessage = "Listening..."
                }

                override fun onRmsChanged(rmsdB: Float) = Unit

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    isListening = false
                    statusMessage = "Processing speech..."
                }

                override fun onError(error: Int) {
                    isListening = false
                    if (transcript.isNotBlank()) {
                        handleRecognizedSpeech(transcript)
                        return
                    }
                    statusMessage = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't understand. Try again."
                        SpeechRecognizer.ERROR_AUDIO -> "Microphone input is unavailable."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy. Try again."
                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                        SpeechRecognizer.ERROR_SERVER -> "Speech recognition service is unavailable."
                        SpeechRecognizer.ERROR_CLIENT -> "Voice input was cancelled."
                        else -> "Unable to capture speech."
                    }
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    val spokenText = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()

                    if (spokenText.isBlank()) {
                        statusMessage = "No speech was captured."
                        return
                    }

                    handleRecognizedSpeech(spokenText)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partialText = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    if (partialText.isNotBlank()) {
                        transcript = partialText
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

    LaunchedEffect(Unit) {
        refreshAvailability()
    }

    DisposableEffect(generativeModel) {
        onDispose {
            generativeModel.close()
        }
    }

    fun dismissDialog() {
        isListening = false
        pendingSpeechStart = false
        speechRecognizer?.cancel()
        showDialog = false
    }

    IconButton(
        onClick = {
            resetDialogState()
            showDialog = true
            scope.launch {
                refreshAvailability()
                when (availability) {
                    FeatureStatus.AVAILABLE -> startSpeechRecognition()
                    FeatureStatus.UNAVAILABLE -> {
                        statusMessage = "Gemini Nano is unavailable on this device. Using the basic voice parser."
                        startSpeechRecognition()
                    }
                    FeatureStatus.DOWNLOADABLE -> {
                        statusMessage = "Gemini Nano status: downloadable. The ML Kit model can be downloaded on this device."
                    }
                    FeatureStatus.DOWNLOADING -> {
                        statusMessage = "Gemini Nano status: downloading. AICore is still preparing the model."
                    }
                    else -> {
                        statusMessage = "Checking Gemini Nano availability..."
                    }
                }
            }
        }
    ) {
        Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = "Voice expense"
        )
    }

    if (!showDialog) {
        return
    }

    AlertDialog(
        onDismissRequest = { dismissDialog() },
        title = {
            Text("Voice expense")
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
                        text = buildAndroidVoiceAvailabilitySummary(availability),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (isCheckingAvailability || isParsing || isSaving || isDownloading || isListening) {
                    CircularProgressIndicator()
                }

                if (transcript.isNotBlank()) {
                    VoiceExpenseSection(
                        title = "Transcript",
                        body = transcript
                    )
                }

                draft?.let { currentDraft ->
                    VoiceExpenseSection(
                        title = if (currentDraft.action == AndroidVoiceExpenseActionKind.Update) {
                            "Update"
                        } else {
                            "New expense"
                        },
                        body = buildAndroidVoiceExpenseSummary(currentDraft)
                    )
                }

                if (transcript.isBlank() && draft == null && statusMessage == null) {
                    Text(
                        text = "Speak an expense command to create or update an entry.",
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
                                runCatching {
                                    persistAndroidVoiceExpenseDraft(
                                        draft = currentDraft,
                                        repository = repository
                                    )
                                }.onSuccess {
                                    showDialog = false
                                }.onFailure { error ->
                                    statusMessage = error.message ?: "Unable to save expense."
                                }
                                isSaving = false
                            }
                        }
                    ) {
                        Text(
                            if (draft?.action == AndroidVoiceExpenseActionKind.Update) {
                                "Update expense"
                            } else {
                                "Save expense"
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
                                statusMessage = "Downloading Gemini Nano for ML Kit..."
                                runCatching {
                                    generativeModel.download().collect()
                                    refreshAvailability()
                                }.onSuccess {
                                    if (availability == FeatureStatus.AVAILABLE) {
                                        statusMessage = "Gemini Nano is ready."
                                        startSpeechRecognition()
                                    } else {
                                        statusMessage = "Gemini Nano is still not ready."
                                    }
                                }.onFailure { error ->
                                    statusMessage = error.message ?: "Unable to download Gemini Nano."
                                }
                                isDownloading = false
                            }
                        }
                    ) {
                        Text("Download model")
                    }
                }
                availability == FeatureStatus.AVAILABLE || availability == FeatureStatus.UNAVAILABLE -> {
                    Button(
                        enabled = !isCheckingAvailability && !isParsing,
                        onClick = {
                            if (isListening) {
                                stopSpeechRecognition()
                            } else {
                                startSpeechRecognition()
                            }
                        }
                    ) {
                        Text(
                            when {
                                isListening -> "Stop listening"
                                transcript.isBlank() -> "Start listening"
                                else -> "Listen again"
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
                        Text("Refresh")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = { dismissDialog() }
            ) {
                Text("Close")
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

private suspend fun loadAndroidVoiceExpenseSnapshot(
    repository: ExpenseRepository,
    strings: Strings
): AndroidVoiceExpenseSnapshot {
    repository.insertDefaultCategoriesIfEmpty()

    val categorySnapshot = repository.getAllCategoriesSnapshot()
    val categories = categorySnapshot
        .sortedBy { strings.categoryName(it.id, it.name, it.isCustom).lowercase(Locale.getDefault()) }
        .map { category ->
            AndroidVoiceExpenseCategory(
                id = category.id,
                name = strings.categoryName(category.id, category.name, category.isCustom)
            )
        }

    val categoriesById = categorySnapshot.associateBy(Category::id)

    val recentExpenses = repository.getAllExpensesSnapshot()
        .sortedByDescending(Expense::date)
        .take(120)
        .mapNotNull { expense ->
            val category = categoriesById[expense.categoryId] ?: return@mapNotNull null
            AndroidVoiceExpenseCandidate(
                id = expense.id,
                amountInput = formatAmountInput(expense.amount),
                categoryId = expense.categoryId,
                categoryName = strings.categoryName(category.id, category.name, category.isCustom),
                description = expense.description,
                date = expense.date,
                isShared = expense.isShared == 1L
            )
        }

    return AndroidVoiceExpenseSnapshot(
        categories = categories,
        recentExpenses = recentExpenses
    )
}

private suspend fun interpretAndroidVoiceExpense(
    transcript: String,
    snapshot: AndroidVoiceExpenseSnapshot,
    generativeModel: GenerativeModel,
    availability: Int?
): AndroidVoiceExpenseInterpretation {
    if (availability == FeatureStatus.UNAVAILABLE) {
        return parseSimpleAndroidVoiceExpenseIntent(
            transcript = transcript,
            snapshot = snapshot
        )
    }

    val prompt = buildAndroidVoiceExpensePrompt(
        transcript = transcript,
        categories = snapshot.categories,
        expenses = snapshot.recentExpenses
    )

    val response = withContext(Dispatchers.IO) {
        generativeModel.generateContent(prompt)
    }

    val rawResponse = response.candidates.firstOrNull()?.text.orEmpty()
    if (rawResponse.isBlank()) {
        return AndroidVoiceExpenseInterpretation(
            action = AndroidVoiceExpenseActionKind.Ignore,
            targetExpenseId = null,
            amountInput = null,
            categoryName = null,
            description = null,
            date = null,
            isShared = false
        )
    }

    val jsonPayload = extractAndroidVoiceExpenseJson(rawResponse)
    val action = when (jsonPayload.optString("action", "ignore").trim().lowercase(Locale.US)) {
        "create" -> AndroidVoiceExpenseActionKind.Create
        "update" -> AndroidVoiceExpenseActionKind.Update
        else -> AndroidVoiceExpenseActionKind.Ignore
    }

    val date = jsonPayload.optString("date")
        .trim()
        .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        ?.let(LocalDate::parse)

    return AndroidVoiceExpenseInterpretation(
        action = action,
        targetExpenseId = jsonPayload.optString("targetExpenseId")
            .trim()
            .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) },
        amountInput = jsonPayload.optString("amount")
            .trim()
            .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) },
        categoryName = jsonPayload.optString("categoryName")
            .trim()
            .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) },
        description = jsonPayload.optString("description")
            .trim()
            .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) },
        date = date,
        isShared = jsonPayload.optBoolean("shared", false)
    )
}

private fun resolveAndroidVoiceExpenseDraft(
    interpretation: AndroidVoiceExpenseInterpretation,
    snapshot: AndroidVoiceExpenseSnapshot,
    transcript: String
): AndroidVoiceExpenseDraft? {
    if (interpretation.action == AndroidVoiceExpenseActionKind.Ignore) {
        return null
    }

    val amountInput = interpretation.amountInput
        ?.replace(',', '.')
        ?.takeIf { parseAmountInput(it) != null }
        ?: return null

    val category = matchAndroidVoiceExpenseCategory(
        requestedCategoryName = interpretation.categoryName,
        categories = snapshot.categories,
        transcript = transcript
    ) ?: return null

    val date = (interpretation.date ?: currentSystemLocalDate())
        .atStartOfDayIn(TimeZone.currentSystemDefault())
        .toEpochMilliseconds()

    return when (interpretation.action) {
        AndroidVoiceExpenseActionKind.Create -> {
            AndroidVoiceExpenseDraft(
                action = AndroidVoiceExpenseActionKind.Create,
                expenseId = null,
                amountInput = amountInput,
                categoryId = category.id,
                categoryName = category.name,
                description = interpretation.description,
                date = date,
                isShared = interpretation.isShared
            )
        }
        AndroidVoiceExpenseActionKind.Update -> {
            val expenseId = interpretation.targetExpenseId ?: return null
            val existingExpense = snapshot.recentExpenses.firstOrNull { it.id == expenseId } ?: return null
            AndroidVoiceExpenseDraft(
                action = AndroidVoiceExpenseActionKind.Update,
                expenseId = existingExpense.id,
                amountInput = amountInput,
                categoryId = category.id,
                categoryName = category.name,
                description = interpretation.description,
                date = date,
                isShared = interpretation.isShared
            )
        }
        AndroidVoiceExpenseActionKind.Ignore -> null
    }
}

private suspend fun persistAndroidVoiceExpenseDraft(
    draft: AndroidVoiceExpenseDraft,
    repository: ExpenseRepository
) {
    val amount = parseAmountInput(draft.amountInput)
        ?: error("Invalid amount")
    require(amount > com.ionspin.kotlin.bignum.integer.BigInteger.ZERO) {
        "Amount must be greater than zero"
    }

    when (draft.action) {
        AndroidVoiceExpenseActionKind.Create -> {
            repository.insertExpenses(
                expenses = listOf(
                    PendingExpense(
                        id = buildAndroidVoiceExpenseId(),
                        amount = amount,
                        date = draft.date,
                        categoryId = draft.categoryId,
                        description = draft.description?.takeIf { it.isNotBlank() },
                        isShared = draft.isShared,
                        recurringSeriesId = null
                    )
                )
            )
        }
        AndroidVoiceExpenseActionKind.Update -> {
            val expenseId = draft.expenseId ?: error("Expense id is missing")
            val existingExpense = repository.getExpenseById(expenseId) ?: error("Expense not found")
            repository.insertExpenses(
                expenses = listOf(
                    PendingExpense(
                        id = existingExpense.id,
                        amount = amount,
                        date = draft.date,
                        categoryId = draft.categoryId,
                        description = draft.description?.takeIf { it.isNotBlank() },
                        isShared = draft.isShared,
                        recurringSeriesId = existingExpense.recurringSeriesId
                    )
                )
            )
        }
        AndroidVoiceExpenseActionKind.Ignore -> {
            error("Nothing to save")
        }
    }
}

private fun buildAndroidVoiceExpensePrompt(
    transcript: String,
    categories: List<AndroidVoiceExpenseCategory>,
    expenses: List<AndroidVoiceExpenseCandidate>
): String {
    val today = currentSystemLocalDate().toString()
    val categoryList = categories.joinToString(separator = "\n") { category ->
        "- ${category.name}"
    }
    val expenseList = expenses.joinToString(separator = "\n") { expense ->
        "- id=${expense.id} | amount=${expense.amountInput} | category=${expense.categoryName} | description=${expense.description.orEmpty()} | date=${formatAndroidVoiceExpenseDate(expense.date)} | shared=${expense.isShared}"
    }

    return """
        You extract a household expense action from a spoken command.
        The input may be in Italian or English.
        
        Today's date: $today
        
        Valid categories:
        $categoryList
        
        Recent expenses that may be updated:
        $expenseList
        
        Rules:
        - Return JSON only. Do not wrap it in markdown.
        - Use action=create when the user is adding a new expense.
        - Use action=update only when the user clearly means one of the listed recent expenses. When you do that, copy its id exactly into targetExpenseId.
        - Use action=ignore when the transcript is not a usable expense command.
        - amount must be a string using a dot decimal separator and exactly two decimals, for example 12.50.
        - categoryName must exactly match one of the valid categories above.
        - date must be in YYYY-MM-DD format. If the user does not specify a date, use today's date.
        - shared must be true only when the user clearly says the expense is shared or split.
        - description should be short and useful. Omit it when none is needed.
        
        Return this schema:
        {"action":"create|update|ignore","targetExpenseId":"string or null","amount":"12.50 or null","categoryName":"exact category or null","description":"string or null","date":"YYYY-MM-DD or null","shared":true}
        
        Transcript:
        $transcript
    """.trimIndent()
}

private fun extractAndroidVoiceExpenseJson(rawResponse: String): JSONObject {
    val cleaned = rawResponse
        .removePrefix("```json")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()
    val start = cleaned.indexOf('{')
    val end = cleaned.lastIndexOf('}')
    val jsonText = if (start >= 0 && end > start) {
        cleaned.substring(start, end + 1)
    } else {
        cleaned
    }
    return JSONObject(jsonText)
}

private fun matchAndroidVoiceExpenseCategory(
    requestedCategoryName: String?,
    categories: List<AndroidVoiceExpenseCategory>,
    transcript: String? = null
): AndroidVoiceExpenseCategory? {
    if (!requestedCategoryName.isNullOrBlank()) {
        return categories.firstOrNull { it.name.equals(requestedCategoryName, ignoreCase = true) }
            ?: categories.firstOrNull {
                normalizeAndroidVoiceExpenseToken(it.name) == normalizeAndroidVoiceExpenseToken(requestedCategoryName)
            }
    }

    if (transcript.isNullOrBlank()) {
        return null
    }

    val searchText = transcript.lowercase(Locale.getDefault())
    return categories.firstOrNull { category ->
        androidVoiceExpenseCategoryAliases(category).any { alias ->
            searchText.contains(alias.lowercase(Locale.getDefault()))
        }
    }
}

private fun normalizeAndroidVoiceExpenseToken(value: String): String {
    val normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
    return buildString(normalized.length) {
        normalized.forEach { character ->
            if (Character.getType(character) != Character.NON_SPACING_MARK.toInt() && character.isLetterOrDigit()) {
                append(character.lowercaseChar())
            }
        }
    }
}

private fun parseSimpleAndroidVoiceExpenseIntent(
    transcript: String,
    snapshot: AndroidVoiceExpenseSnapshot
): AndroidVoiceExpenseInterpretation {
    val amountInput = parseSimpleAndroidVoiceExpenseAmount(transcript)
    val categoryName = matchAndroidVoiceExpenseCategory(
        requestedCategoryName = null,
        categories = snapshot.categories,
        transcript = transcript
    )?.name

    return AndroidVoiceExpenseInterpretation(
        action = if (amountInput != null && categoryName != null) {
            AndroidVoiceExpenseActionKind.Create
        } else {
            AndroidVoiceExpenseActionKind.Ignore
        },
        targetExpenseId = null,
        amountInput = amountInput,
        categoryName = categoryName,
        description = null,
        date = parseRelativeAndroidVoiceExpenseDate(transcript),
        isShared = parseAndroidVoiceSharedFlag(transcript)
    )
}

private fun parseSimpleAndroidVoiceExpenseAmount(transcript: String): String? {
    val amountMatch = SIMPLE_ANDROID_VOICE_AMOUNT_REGEX.find(transcript) ?: return null
    val normalized = amountMatch.value
        .replace(',', '.')
        .filterNot { it == ' ' }
    val parts = normalized.split('.', limit = 2)
    val whole = parts.firstOrNull()?.filter(Char::isDigit).orEmpty()
    val decimals = parts.getOrNull(1)?.filter(Char::isDigit).orEmpty()
    if (whole.isBlank()) {
        return null
    }
    if (decimals.length > 2) {
        return null
    }
    val normalizedAmount = buildString {
        append(whole)
        append('.')
        append(decimals.padEnd(2, '0'))
    }
    return normalizedAmount.takeIf { parseAmountInput(it) != null }
}

private fun parseRelativeAndroidVoiceExpenseDate(transcript: String): LocalDate? {
    val normalizedTranscript = normalizeAndroidVoiceExpenseToken(transcript)
    val today = currentSystemLocalDate()
    val timeZone = TimeZone.currentSystemDefault()
    return ANDROID_VOICE_RELATIVE_DATE_OFFSETS.firstNotNullOfOrNull { (terms, offset) ->
        terms.firstOrNull { normalizedTranscript.contains(it) }?.let {
            Instant.fromEpochMilliseconds(
                today.atStartOfDayIn(timeZone).toEpochMilliseconds() + (offset * 86_400_000L)
            ).toLocalDateTime(timeZone).date
        }
    }
}

private fun parseAndroidVoiceSharedFlag(transcript: String): Boolean {
    val normalizedTranscript = normalizeAndroidVoiceExpenseToken(transcript)
    return ANDROID_VOICE_SHARED_TERMS.any(normalizedTranscript::contains)
}

private fun androidVoiceExpenseCategoryAliases(category: AndroidVoiceExpenseCategory): List<String> {
    val normalizedName = normalizeAndroidVoiceExpenseToken(category.name)
    return listOf(category.name) + (ANDROID_VOICE_DEFAULT_CATEGORY_ALIASES[normalizedName] ?: emptyList())
}

private fun buildAndroidVoiceExpenseSummary(draft: AndroidVoiceExpenseDraft): String {
    return buildString {
        appendLine("Amount: ${draft.amountInput}")
        appendLine("Category: ${draft.categoryName}")
        appendLine("Date: ${formatAndroidVoiceExpenseDate(draft.date)}")
        appendLine("Shared: ${if (draft.isShared) "Yes" else "No"}")
        draft.description?.takeIf { it.isNotBlank() }?.let { description ->
            append("Description: $description")
        }
    }.trim()
}

private fun buildAndroidVoiceAvailabilitySummary(status: Int?): String {
    return when (status) {
        null -> "Checking Gemini Nano availability..."
        FeatureStatus.AVAILABLE -> "Gemini Nano status: available."
        FeatureStatus.DOWNLOADABLE -> "Gemini Nano status: downloadable."
        FeatureStatus.DOWNLOADING -> "Gemini Nano status: downloading."
        FeatureStatus.UNAVAILABLE -> "Gemini Nano status: unavailable. Basic parser fallback is enabled."
        else -> "Gemini Nano status: unknown."
    }
}

private fun formatAndroidVoiceExpenseDate(dateMillis: Long): String {
    return Instant.fromEpochMilliseconds(dateMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
        .toString()
}

private fun currentSystemLocalDate(): LocalDate {
    return Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
}

private fun buildAndroidVoiceExpenseId(): String {
    return "${Clock.System.now().toEpochMilliseconds()}-${Random.nextLong()}"
}

private val SIMPLE_ANDROID_VOICE_AMOUNT_REGEX = Regex("""\d+(?:[.,]\d{1,2})?""")

private val ANDROID_VOICE_RELATIVE_DATE_OFFSETS = listOf(
    listOf("today", "oggi") to 0,
    listOf("yesterday", "ieri") to -1,
    listOf("tomorrow", "domani") to 1
)

private val ANDROID_VOICE_SHARED_TERMS = listOf(
    "shared",
    "split",
    "share",
    "condivisa",
    "condividere",
    "divisa"
)

private val ANDROID_VOICE_DEFAULT_CATEGORY_ALIASES: Map<String, List<String>> = mapOf(
    "groceries" to listOf("grocery", "food", "supermarket"),
    "spesa" to listOf("supermercato", "cibo", "alimentari"),
    "transport" to listOf("bus", "train", "taxi", "metro"),
    "trasporti" to listOf("autobus", "treno", "taxi", "metro"),
    "entertainment" to listOf("cinema", "movie", "netflix"),
    "svago" to listOf("cinema", "film", "netflix"),
    "bills" to listOf("utilities", "electricity", "water", "gas"),
    "bollette" to listOf("luce", "acqua", "gas"),
    "health" to listOf("doctor", "medicine", "pharmacy"),
    "salute" to listOf("medico", "farmacia", "medicina"),
    "shopping" to listOf("clothes", "clothing", "purchase"),
    "shoppingabbigliamento" to listOf("vestiti", "abbigliamento", "acquisto"),
    "rent" to listOf("house", "apartment"),
    "affitto" to listOf("casa", "appartamento")
)
