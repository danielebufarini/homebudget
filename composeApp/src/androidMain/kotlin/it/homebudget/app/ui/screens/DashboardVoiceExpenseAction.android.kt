package it.homebudget.app.ui.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.speech.RecognizerIntent
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
import androidx.compose.ui.unit.dp
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.data.PendingExpense
import it.homebudget.app.data.formatAmountInput
import it.homebudget.app.data.parseAmountInput
import it.homebudget.app.database.Category
import it.homebudget.app.database.Expense
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

    var availability by remember { mutableStateOf<Int?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var transcript by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf<AndroidVoiceExpenseDraft?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isCheckingAvailability by remember { mutableStateOf(false) }
    var isParsing by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }

    fun resetDialogState() {
        transcript = ""
        draft = null
        statusMessage = null
        isCheckingAvailability = false
        isParsing = false
        isSaving = false
        isDownloading = false
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

    fun launchSpeechRecognizer(launcher: androidx.activity.result.ActivityResultLauncher<Intent>) {
        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak an expense command")
        }

        runCatching {
            launcher.launch(recognizerIntent)
        }.onFailure { error ->
            statusMessage = when (error) {
                is ActivityNotFoundException -> "No speech recognizer is available on this device."
                else -> error.message ?: "Unable to start speech recognition."
            }
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            statusMessage = "Voice input was cancelled."
            return@rememberLauncherForActivityResult
        }

        val spokenText = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()

        if (spokenText.isBlank()) {
            statusMessage = "No speech was captured."
            return@rememberLauncherForActivityResult
        }

        transcript = spokenText
        draft = null
        statusMessage = "Interpreting voice input..."

        scope.launch {
            isParsing = true
            val parseResult = runCatching {
                val snapshot = loadAndroidVoiceExpenseSnapshot(repository)
                val interpretation = interpretAndroidVoiceExpense(
                    transcript = spokenText,
                    snapshot = snapshot,
                    generativeModel = generativeModel
                )
                resolveAndroidVoiceExpenseDraft(
                    interpretation = interpretation,
                    snapshot = snapshot
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

    LaunchedEffect(Unit) {
        refreshAvailability()
    }

    DisposableEffect(generativeModel) {
        onDispose {
            generativeModel.close()
        }
    }

    IconButton(
        onClick = {
            resetDialogState()
            showDialog = true
            scope.launch {
                refreshAvailability()
                when (availability) {
                    FeatureStatus.AVAILABLE -> launchSpeechRecognizer(speechLauncher)
                    FeatureStatus.DOWNLOADABLE -> {
                        statusMessage = "Gemini Nano status: downloadable. The ML Kit model can be downloaded on this device."
                    }
                    FeatureStatus.DOWNLOADING -> {
                        statusMessage = "Gemini Nano status: downloading. AICore is still preparing the model."
                    }
                    FeatureStatus.UNAVAILABLE -> {
                        statusMessage = "Gemini Nano status: unavailable. This device or emulator does not currently support the ML Kit Prompt API."
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
        onDismissRequest = { showDialog = false },
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

                if (isCheckingAvailability || isParsing || isSaving || isDownloading) {
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
                                        launchSpeechRecognizer(speechLauncher)
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
                availability == FeatureStatus.AVAILABLE -> {
                    Button(
                        enabled = !isCheckingAvailability && !isParsing,
                        onClick = { launchSpeechRecognizer(speechLauncher) }
                    ) {
                        Text(if (transcript.isBlank()) "Start listening" else "Listen again")
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
                onClick = { showDialog = false }
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
    repository: ExpenseRepository
): AndroidVoiceExpenseSnapshot {
    repository.insertDefaultCategoriesIfEmpty()

    val categorySnapshot = repository.getAllCategoriesSnapshot()
    val categories = categorySnapshot
        .sortedBy { it.name.lowercase(Locale.getDefault()) }
        .map { category ->
            AndroidVoiceExpenseCategory(
                id = category.id,
                name = category.name
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
                categoryName = category.name,
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
    generativeModel: GenerativeModel
): AndroidVoiceExpenseInterpretation {
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
    snapshot: AndroidVoiceExpenseSnapshot
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
        categories = snapshot.categories
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
    categories: List<AndroidVoiceExpenseCategory>
): AndroidVoiceExpenseCategory? {
    if (requestedCategoryName.isNullOrBlank()) {
        return null
    }

    return categories.firstOrNull { it.name.equals(requestedCategoryName, ignoreCase = true) }
        ?: categories.firstOrNull {
            normalizeAndroidVoiceExpenseToken(it.name) == normalizeAndroidVoiceExpenseToken(requestedCategoryName)
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
        FeatureStatus.UNAVAILABLE -> "Gemini Nano status: unavailable."
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
