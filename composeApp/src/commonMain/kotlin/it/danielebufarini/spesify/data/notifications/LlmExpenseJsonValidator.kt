package it.danielebufarini.spesify.data.notifications

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class LlmExpenseJsonValidator(
    private val json: Json = Json {
        ignoreUnknownKeys = false
        isLenient = false
    }
) {
    fun validate(
        rawJson: String,
        minConfidence: Float = DEFAULT_MIN_CONFIDENCE
    ): ExpenseTextInterpretation? {
        val decoded = runCatching {
            json.decodeFromString(LlmExpenseJsonPayload.serializer(), rawJson.trim())
        }.getOrNull() ?: return null

        if (decoded.isExpense != true) return null
        val amountMinor = decoded.amount?.parseDecimalAmountMinorOrNull() ?: return null
        if (amountMinor <= 0L) return null

        val currency = decoded.currency
            ?.trim()
            ?.uppercase()
            ?.takeIf { it.isNotBlank() }
            ?: SUPPORTED_CURRENCY
        if (currency != SUPPORTED_CURRENCY) return null

        val confidence = decoded.confidence?.takeIf { it in 0f..1f } ?: return null
        if (confidence < minConfidence) return null

        return ExpenseTextInterpretation(
            amountMinor = amountMinor,
            merchant = decoded.merchant?.trim()?.takeIf { it.isNotBlank() },
            currency = currency,
            confidence = confidence,
            source = InterpretationSource.LocalLlm
        )
    }

    private fun String.parseDecimalAmountMinorOrNull(): Long? {
        val trimmed = trim()
        if (!decimalAmountRegex.matches(trimmed)) return null
        val parts = trimmed.replace(',', '.').split('.')
        val euros = parts[0].toLongOrNull() ?: return null
        val cents = parts.getOrNull(1).orEmpty().padEnd(2, '0').take(2).toLongOrNull() ?: 0L
        val amountMinor = euros.safeMultiply(100L)?.safeAdd(cents) ?: return null
        return amountMinor.takeIf { it > 0L }
    }

    private fun Long.safeMultiply(other: Long): Long? =
        if (this > Long.MAX_VALUE / other) null else this * other

    private fun Long.safeAdd(other: Long): Long? =
        if (this > Long.MAX_VALUE - other) null else this + other

    @Serializable
    private data class LlmExpenseJsonPayload(
        val isExpense: Boolean? = null,
        val amount: String? = null,
        val currency: String? = null,
        val merchant: String? = null,
        val confidence: Float? = null
    )

    private companion object {
        private const val SUPPORTED_CURRENCY = "EUR"
        private const val DEFAULT_MIN_CONFIDENCE = 0.75f
        private val decimalAmountRegex = Regex("^\\d+(?:[.,]\\d{1,2})?$")
    }
}
