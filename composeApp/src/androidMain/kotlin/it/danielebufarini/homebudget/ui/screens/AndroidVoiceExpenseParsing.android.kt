package it.danielebufarini.homebudget.ui.screens

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.GenerativeModel
import it.danielebufarini.homebudget.data.parseAmountInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale
import kotlin.time.Instant

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

private fun buildAndroidVoiceExpensePrompt(
    transcript: String,
    categories: List<AndroidVoiceExpenseCategory>,
    expenses: List<AndroidVoiceExpenseCandidate>
): String {
    val locale = Locale.getDefault()
    val today = currentSystemLocalDate().toString()
    val languageCode = locale.toLanguageTag().takeIf { it.isNotBlank() } ?: locale.language
    val languageName = locale.getDisplayLanguage(locale).takeIf { it.isNotBlank() } ?: languageCode
    val categoryList = categories.joinToString(separator = "\n") { category ->
        "- id=${category.id}, name=${category.name}"
    }.ifBlank { "- none" }
    val expenseList = expenses.joinToString(separator = "\n") { expense ->
        val description = expense.description?.takeIf { it.isNotBlank() } ?: "none"
        val shared = if (expense.isShared) "yes" else "no"
        "- id=${expense.id}, amount=${expense.amountInput}, date=${formatAndroidVoiceExpenseDate(expense.date)}, categoryId=${expense.categoryId}, categoryName=${expense.categoryName}, shared=$shared, description=$description"
    }.ifBlank { "- none" }

    return buildString {
        appendLine(buildVoiceExpensePromptInstructions(buildAndroidVoiceExpenseOutputContract()))
        appendLine()
        append(
            buildVoiceExpensePromptContext(
                currentDate = today,
                currentLanguageName = languageName,
                currentLanguageCode = languageCode,
                transcript = transcript,
                categoriesText = categoryList,
                expensesText = expenseList
            )
        )
    }
}

private fun extractAndroidVoiceExpenseJson(rawResponse: String): JSONObject {
    val cleaned = rawResponse
        .removePrefix("```json")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()
    val start = cleaned.indexOf('{')
    val end = cleaned.lastIndexOf('}')
    val jsonText = if (start in 0..<end) {
        cleaned.substring(start, end + 1)
    } else {
        cleaned
    }
    return JSONObject(jsonText)
}

private fun JSONObject.optNullableString(name: String): String? {
    if (!has(name) || isNull(name)) {
        return null
    }
    return optString(name)
        .trim()
        .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
}

private fun JSONObject.optNullableBoolean(name: String): Boolean? {
    if (!has(name) || isNull(name)) {
        return null
    }
    return when (val value = opt(name)) {
        is Boolean -> value
        is String -> when (value.trim().lowercase(Locale.US)) {
            "true" -> true
            "false" -> false
            else -> null
        }
        else -> null
    }
}

internal fun formatAndroidVoiceExpenseDate(dateMillis: Long): String {
    return Instant.fromEpochMilliseconds(dateMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
        .toString()
}

internal fun currentSystemLocalDate(): LocalDate {
    return kotlin.time.Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
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
    val category = matchAndroidVoiceExpenseCategory(
        requestedCategoryId = null,
        requestedCategoryName = null,
        categories = snapshot.categories,
        transcript = transcript
    )

    return AndroidVoiceExpenseInterpretation(
        action = if (amountInput != null && category != null) {
            AndroidVoiceExpenseActionKind.Create
        } else {
            AndroidVoiceExpenseActionKind.Ignore
        },
        targetExpenseId = null,
        amountInput = amountInput,
        categoryId = category?.id,
        categoryName = category?.name,
        description = null,
        date = parseRelativeAndroidVoiceExpenseDate(transcript),
        isShared = parseAndroidVoiceSharedFlag(transcript),
        summary = null
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
    if (whole.isBlank() || decimals.length > 2) {
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

private fun androidVoiceExpenseCategoryAliases(category: AndroidVoiceExpenseCategory): Set<String> {
    val baseTerms = buildSet {
        val normalizedName = normalizeAndroidVoiceExpenseToken(category.name)
        if (normalizedName.isNotBlank()) {
            add(normalizedName)
        }

        tokenizeAndroidVoiceExpenseText(category.name).forEach { token ->
            add(token)
            addAll(ANDROID_VOICE_TOKEN_SYNONYMS[token].orEmpty())
        }
    }

    return baseTerms.filterTo(linkedSetOf()) { it.length >= 3 }
}

private fun tokenizeAndroidVoiceExpenseText(value: String): List<String> {
    return value
        .split(ANDROID_VOICE_TOKEN_SPLIT_REGEX)
        .map(::normalizeAndroidVoiceExpenseToken)
        .filter { it.isNotBlank() }
}

private data class CategoryTranscriptMatch(
    val category: AndroidVoiceExpenseCategory,
    val matchCount: Int,
    val longestMatchLength: Int
)

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

private val ANDROID_VOICE_TOKEN_SPLIT_REGEX = Regex("[^\\p{L}\\p{Nd}]+")

private val ANDROID_VOICE_TOKEN_SYNONYMS: Map<String, List<String>> = mapOf(
    "food" to listOf("grocery", "groceries", "supermarket", "restaurant"),
    "cibo" to listOf("spesa", "alimentari", "supermercato", "ristorante"),
    "home" to listOf("house", "apartment", "rent", "housing"),
    "casa" to listOf("affitto", "appartamento"),
    "bills" to listOf("utilities", "electricity", "water", "gas"),
    "bollette" to listOf("luce", "acqua", "gas"),
    "car" to listOf("auto", "fuel", "petrol", "parking"),
    "auto" to listOf("car", "benzina", "parcheggio"),
    "transport" to listOf("bus", "train", "taxi", "metro"),
    "trasporti" to listOf("autobus", "treno", "taxi", "metro"),
    "entertainment" to listOf("cinema", "movie", "netflix"),
    "svago" to listOf("cinema", "film", "netflix"),
    "health" to listOf("doctor", "medicine", "pharmacy"),
    "salute" to listOf("medico", "medicina", "farmacia"),
    "shopping" to listOf("clothes", "clothing", "purchase"),
    "abbigliamento" to listOf("shopping", "vestiti", "acquisto"),
    "miscellaneous" to listOf("other", "various"),
    "varie" to listOf("altro", "vario")
)
