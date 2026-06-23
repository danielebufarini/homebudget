package it.danielebufarini.spesify.data.notifications

import it.danielebufarini.spesify.data.parsePositiveLocalizedAmountMinorOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class LlmExpenseJsonValidator(
    private val json: Json = Json {
        ignoreUnknownKeys = false
        isLenient = false
    },
    private val evidenceParser: ExpenseNotificationTextParser = ExpenseNotificationTextParser()
) {
    fun validate(
        rawJson: String,
        minConfidence: Float = DEFAULT_MIN_CONFIDENCE,
        ocrText: String? = null
    ): ExpenseTextInterpretation? = validateAll(
        rawJson = rawJson,
        minConfidence = minConfidence,
        ocrText = ocrText
    ).firstOrNull()

    fun validateAll(
        rawJson: String,
        minConfidence: Float = DEFAULT_MIN_CONFIDENCE,
        ocrText: String? = null
    ): List<ExpenseTextInterpretation> {
        val payload = rawJson.trim().takeIf { it.isNotBlank() } ?: return emptyList()
        val decodedTransactions = decodeTransactions(payload) ?: return emptyList()
        val monetaryEvidence = ocrText
            ?.let(evidenceParser::monetaryAmountMinorEvidence)
            .orEmpty()

        return decodedTransactions
            .mapNotNull { decoded ->
                decoded.toInterpretation(
                    minConfidence = minConfidence,
                    monetaryEvidence = monetaryEvidence,
                    ocrText = ocrText
                )
            }
            .distinctBy { it.amountMinor to it.merchant.orEmpty().uppercase() }
    }

    private fun decodeTransactions(rawJson: String): List<LlmExpenseJsonPayload>? {
        decodeTransactionsPayload(rawJson)?.let { return it.transactions }
        decodeSinglePayload(rawJson)?.let { return listOf(it) }
        return null
    }

    private fun decodeTransactionsPayload(rawJson: String): LlmExpenseTransactionsJsonPayload? =
        try {
            json.decodeFromString(LlmExpenseTransactionsJsonPayload.serializer(), rawJson)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun decodeSinglePayload(rawJson: String): LlmExpenseJsonPayload? =
        try {
            json.decodeFromString(LlmExpenseJsonPayload.serializer(), rawJson)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun LlmExpenseJsonPayload.toInterpretation(
        minConfidence: Float,
        monetaryEvidence: Set<Long>,
        ocrText: String?
    ): ExpenseTextInterpretation? {
        if (isExpense != true) return null
        val amountMinor = amountMinor?.takeIf { it > 0L } ?: return null
        val amountTextMinor = amountText
            ?.takeIf { it.isNotBlank() }
            ?.let(::parsePositiveLocalizedAmountMinorOrNull)
        val amountTextAppearsInOcr = amountText != null &&
            ocrText?.normalizedEvidenceText()?.contains(amountText.normalizedEvidenceText()) == true
        val hasCopiedAmountTextEvidence = amountTextMinor == amountMinor && amountTextAppearsInOcr
        val hasAmountEvidence = when {
            ocrText != null -> amountMinor in monetaryEvidence || hasCopiedAmountTextEvidence
            else -> true
        }
        if (!hasAmountEvidence) return null

        val currency = currency
            ?.trim()
            ?.uppercase()
            ?.takeIf { it.isNotBlank() }
            ?: SUPPORTED_CURRENCY
        if (currency != SUPPORTED_CURRENCY) return null

        val confidence = confidence?.takeIf { it in 0f..1f } ?: return null
        if (confidence < minConfidence) return null

        return ExpenseTextInterpretation(
            amountMinor = amountMinor,
            merchant = merchant?.trim()?.takeIf { it.isNotBlank() },
            currency = currency,
            confidence = confidence,
            source = InterpretationSource.LocalLlm
        )
    }

    private fun String.normalizedEvidenceText(): String =
        trim()
            .replace(Regex("\\s+"), "")
            .uppercase()

    @Serializable
    private data class LlmExpenseTransactionsJsonPayload(
        val transactions: List<LlmExpenseJsonPayload>
    )

    @Serializable
    private data class LlmExpenseJsonPayload(
        val isExpense: Boolean? = null,
        val amountMinor: Long? = null,
        val amountText: String? = null,
        val currency: String? = null,
        val merchant: String? = null,
        val confidence: Float? = null
    )

    private companion object {
        private const val SUPPORTED_CURRENCY = "EUR"
        private const val DEFAULT_MIN_CONFIDENCE = 0.75f
    }
}
