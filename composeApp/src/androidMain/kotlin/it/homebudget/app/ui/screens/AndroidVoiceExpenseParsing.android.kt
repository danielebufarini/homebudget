package it.homebudget.app.ui.screens

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.GenerativeModel
import it.homebudget.app.data.parseAmountInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.json.JSONObject
import java.text.Normalizer
import java.util.*
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

internal fun matchAndroidVoiceExpenseCategory(
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

    return when (Locale.getDefault().language.lowercase(Locale.ROOT)) {
        "it" -> {
            """
            Estrai un'azione relativa a una spesa domestica da un comando vocale.
            L'input può essere in italiano o in inglese.

            Data di oggi: $today

            Categorie valide:
            $categoryList

            Spese recenti che potrebbero essere aggiornate:
            $expenseList

            Regole:
            - Restituisci solo JSON. Non usare markdown.
            - Usa action=create quando l'utente sta aggiungendo una nuova spesa.
            - Usa action=update solo quando l'utente intende chiaramente una delle spese recenti elencate. In quel caso copia il suo id esattamente in targetExpenseId.
            - Usa action=ignore quando la trascrizione non descrive un comando di spesa utilizzabile.
            - amount deve essere una stringa con separatore decimale punto e esattamente due decimali, per esempio 12.50.
            - categoryName deve corrispondere esattamente a una delle categorie valide sopra.
            - date deve essere nel formato YYYY-MM-DD. Se l'utente non specifica una data, usa la data di oggi.
            - shared deve essere true solo quando l'utente dice chiaramente che la spesa è condivisa o divisa.
            - description deve essere breve e utile. Omettila quando non serve.

            Restituisci questo schema:
            {"action":"create|update|ignore","targetExpenseId":"string or null","amount":"12.50 or null","categoryName":"exact category or null","description":"string or null","date":"YYYY-MM-DD or null","shared":true}

            Trascrizione:
            $transcript
            """.trimIndent()
        }
        else -> {
            """
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
    val jsonText = if (start >= 0 && end > start) {
        cleaned.substring(start, end + 1)
    } else {
        cleaned
    }
    return JSONObject(jsonText)
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
