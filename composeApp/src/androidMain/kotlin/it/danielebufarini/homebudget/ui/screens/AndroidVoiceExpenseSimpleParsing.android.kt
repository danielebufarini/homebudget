package it.danielebufarini.homebudget.ui.screens

import it.danielebufarini.homebudget.data.parseAmountInput
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import java.text.Normalizer
import kotlin.time.Instant

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

internal fun normalizeAndroidVoiceExpenseToken(value: String): String {
    val normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
    return buildString(normalized.length) {
        normalized.forEach { character ->
            if (Character.getType(character) != Character.NON_SPACING_MARK.toInt() && character.isLetterOrDigit()) {
                append(character.lowercaseChar())
            }
        }
    }
}

internal fun parseSimpleAndroidVoiceExpenseIntent(
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

internal fun androidVoiceExpenseCategoryAliases(category: AndroidVoiceExpenseCategory): Set<String> {
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

internal data class CategoryTranscriptMatch(
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
