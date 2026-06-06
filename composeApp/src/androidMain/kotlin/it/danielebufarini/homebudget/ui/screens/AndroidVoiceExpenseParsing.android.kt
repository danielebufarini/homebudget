package it.danielebufarini.homebudget.ui.screens

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.GenerativeModel
import it.danielebufarini.homebudget.data.parseAmountInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import java.util.Locale

// Transcript interpretation and category/date matching for Android voice expense input.

internal suspend fun interpretAndroidVoiceExpense(
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
            categoryId = null,
            categoryName = null,
            description = null,
            date = null,
            isShared = null,
            summary = null
        )
    }

    val jsonPayload = extractAndroidVoiceExpenseJson(rawResponse)
    val action = when (
        (jsonPayload.optNullableString("action") ?: jsonPayload.optNullableString("intent") ?: "ignore")
            .trim()
            .lowercase(Locale.US)
    ) {
        "create" -> AndroidVoiceExpenseActionKind.Create
        "update" -> AndroidVoiceExpenseActionKind.Update
        "needclarification", "need_clarification", "need-clarification" -> AndroidVoiceExpenseActionKind.NeedClarification
        else -> AndroidVoiceExpenseActionKind.Ignore
    }

    val date = jsonPayload.optNullableString("date")
        ?.let { value -> runCatching { LocalDate.parse(value) }.getOrNull() }

    return AndroidVoiceExpenseInterpretation(
        action = action,
        targetExpenseId = jsonPayload.optNullableString("targetExpenseId")
            ?: jsonPayload.optNullableString("expenseId"),
        amountInput = jsonPayload.optNullableString("amount"),
        categoryId = jsonPayload.optNullableString("categoryId"),
        categoryName = jsonPayload.optNullableString("categoryName"),
        description = jsonPayload.optNullableString("description"),
        date = date,
        isShared = jsonPayload.optNullableBoolean("shared")
            ?: jsonPayload.optNullableBoolean("isShared"),
        summary = jsonPayload.optNullableString("summary")
    )
}

internal fun matchAndroidVoiceExpenseCategory(
    requestedCategoryId: String? = null,
    requestedCategoryName: String?,
    categories: List<AndroidVoiceExpenseCategory>,
    transcript: String? = null
): AndroidVoiceExpenseCategory? {
    if (!requestedCategoryId.isNullOrBlank()) {
        categories.firstOrNull { it.id == requestedCategoryId }?.let { return it }
    }

    if (!requestedCategoryName.isNullOrBlank()) {
        return categories.firstOrNull { it.name.equals(requestedCategoryName, ignoreCase = true) }
            ?: categories.firstOrNull {
                normalizeAndroidVoiceExpenseToken(it.name) == normalizeAndroidVoiceExpenseToken(requestedCategoryName)
            }
    }

    if (transcript.isNullOrBlank()) {
        return null
    }

    val normalizedTranscript = normalizeAndroidVoiceExpenseToken(transcript)
    return categories
        .mapNotNull { category ->
            val aliases = androidVoiceExpenseCategoryAliases(category)
            val matchCount = aliases.count(normalizedTranscript::contains)
            val longestMatch = aliases
                .filter(normalizedTranscript::contains)
                .maxOfOrNull(String::length)
                ?: return@mapNotNull null
            CategoryTranscriptMatch(
                category = category,
                matchCount = matchCount,
                longestMatchLength = longestMatch
            )
        }
        .maxWithOrNull(
            compareBy<CategoryTranscriptMatch> { it.matchCount }
                .thenBy { it.longestMatchLength }
        )
        ?.category
}

internal fun resolveAndroidVoiceExpenseDraft(
    interpretation: AndroidVoiceExpenseInterpretation,
    snapshot: AndroidVoiceExpenseSnapshot,
    transcript: String
): AndroidVoiceExpenseDraft? {
    return when (interpretation.action) {
        AndroidVoiceExpenseActionKind.Create -> resolveAndroidVoiceExpenseCreateDraft(
            interpretation = interpretation,
            snapshot = snapshot,
            transcript = transcript
        )

        AndroidVoiceExpenseActionKind.Update -> resolveAndroidVoiceExpenseUpdateDraft(
            interpretation = interpretation,
            snapshot = snapshot
        )

        AndroidVoiceExpenseActionKind.NeedClarification,
        AndroidVoiceExpenseActionKind.Ignore -> null
    }
}

private fun resolveAndroidVoiceExpenseCreateDraft(
    interpretation: AndroidVoiceExpenseInterpretation,
    snapshot: AndroidVoiceExpenseSnapshot,
    transcript: String
): AndroidVoiceExpenseDraft? {
    val amountInput = normalizeAndroidVoiceExpenseAmount(interpretation.amountInput) ?: return null
    val category = matchAndroidVoiceExpenseCategory(
        requestedCategoryId = interpretation.categoryId,
        requestedCategoryName = interpretation.categoryName,
        categories = snapshot.categories,
        transcript = transcript
    ) ?: return null

    val date = (interpretation.date ?: currentSystemLocalDate()).toAndroidVoiceExpenseEpochMillis()

    return AndroidVoiceExpenseDraft(
        action = AndroidVoiceExpenseActionKind.Create,
        expenseId = null,
        amountInput = amountInput,
        categoryId = category.id,
        categoryName = category.name,
        description = interpretation.description,
        date = date,
        isShared = interpretation.isShared ?: false
    )
}

private fun resolveAndroidVoiceExpenseUpdateDraft(
    interpretation: AndroidVoiceExpenseInterpretation,
    snapshot: AndroidVoiceExpenseSnapshot
): AndroidVoiceExpenseDraft? {
    val expenseId = interpretation.targetExpenseId ?: return null
    val existingExpense = snapshot.recentExpenses.firstOrNull { it.id == expenseId } ?: return null
    val amountInput = normalizeAndroidVoiceExpenseAmount(interpretation.amountInput)
        ?: existingExpense.amountInput
    val category = matchAndroidVoiceExpenseCategory(
        requestedCategoryId = interpretation.categoryId,
        requestedCategoryName = interpretation.categoryName,
        categories = snapshot.categories,
        transcript = null
    )
        ?: snapshot.categories.firstOrNull { it.id == existingExpense.categoryId }
        ?: AndroidVoiceExpenseCategory(
            id = existingExpense.categoryId,
            name = existingExpense.categoryName
        )

    return AndroidVoiceExpenseDraft(
        action = AndroidVoiceExpenseActionKind.Update,
        expenseId = existingExpense.id,
        amountInput = amountInput,
        categoryId = category.id,
        categoryName = category.name,
        description = interpretation.description ?: existingExpense.description,
        date = interpretation.date?.toAndroidVoiceExpenseEpochMillis() ?: existingExpense.date,
        isShared = interpretation.isShared ?: existingExpense.isShared
    )
}

private fun normalizeAndroidVoiceExpenseAmount(amountInput: String?): String? {
    return amountInput
        ?.replace(',', '.')
        ?.takeIf { parseAmountInput(it) != null }
}

private fun LocalDate.toAndroidVoiceExpenseEpochMillis(): Long {
    return atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
}
