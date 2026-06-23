package it.danielebufarini.spesify.data.notifications

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class LlmExpenseJsonValidator(
    private val json: Json = Json {
        ignoreUnknownKeys = false
        isLenient = false
    },
    private val evidenceParser: ExpenseNotificationTextParser = ExpenseNotificationTextParser(),
    private val extractionValidator: ExpenseLlmExtractionValidator = ExpenseLlmExtractionValidator()
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
        val sourceText = ocrText ?: return emptyList()
        val moneyCandidates = evidenceParser.moneyCandidates(sourceText)
        return extractionValidator.validateAll(
            extractions = decodeAll(rawJson),
            moneyCandidates = moneyCandidates,
            sourceText = sourceText,
            minConfidence = minConfidence
        )
    }

    fun decodeAll(rawJson: String): List<ExpenseLlmExtraction> {
        val payload = rawJson.trim().takeIf { it.isNotBlank() } ?: return emptyList()
        return decodeTransactions(payload)
            ?.mapNotNull { it.toExtraction() }
            .orEmpty()
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

    private fun LlmExpenseJsonPayload.toExtraction(): ExpenseLlmExtraction? {
        val confidence = confidence ?: return null
        return ExpenseLlmExtraction(
            isExpense = isExpense ?: return null,
            selectedAmountMinor = selectedAmountMinor ?: amountMinor,
            currency = currency,
            merchant = merchant,
            confidence = confidence
        )
    }

    @Serializable
    private data class LlmExpenseTransactionsJsonPayload(
        val transactions: List<LlmExpenseJsonPayload>
    )

    @Serializable
    private data class LlmExpenseJsonPayload(
        val isExpense: Boolean? = null,
        val selectedAmountMinor: Long? = null,
        val amountMinor: Long? = null,
        val amountText: String? = null,
        val currency: String? = null,
        val merchant: String? = null,
        val confidence: Float? = null
    )

    private companion object {
        private const val DEFAULT_MIN_CONFIDENCE = 0.75f
    }
}
