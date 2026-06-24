package it.danielebufarini.spesify.data.notifications

data class ExpenseTextCandidate(
    val amountMinor: Long,
    val merchant: String?,
    val currency: String,
    val categoryId: String? = null,
    val dateMillis: Long? = null,
    val source: InterpretationSource,
    val confidence: Float
) {
    val merchantDescription: String? get() = merchant
}

class InterpretExpenseTextUseCase internal constructor(
    private val interpretationPipeline: ExpenseInterpretationPipeline
) {
    suspend fun execute(
        rawText: String,
        canUseLocalLlmFallback: Boolean = false
    ): ExpenseTextCandidate? = executeAll(
        rawText = rawText,
        canUseLocalLlmFallback = canUseLocalLlmFallback
    ).firstOrNull()

    suspend fun executeAll(
        rawText: String,
        canUseLocalLlmFallback: Boolean = false
    ): List<ExpenseTextCandidate> {
        val input = rawText.trim()
        if (input.isBlank()) return emptyList()

        return interpretationPipeline.interpretAll(
            text = input,
            canUseLocalLlmFallback = canUseLocalLlmFallback
        ).mapNotNull { interpretation -> interpretation.toCandidate() }
    }

    private fun ExpenseTextInterpretation.toCandidate(): ExpenseTextCandidate? {
        val amountMinor = amountMinor?.takeIf { it > 0L } ?: return null
        val currency = currency
            ?.trim()
            ?.uppercase()
            ?.takeIf { it == SUPPORTED_CURRENCY }
            ?: return null

        return ExpenseTextCandidate(
            amountMinor = amountMinor,
            merchant = merchant?.trim()?.takeIf { it.isNotBlank() },
            currency = currency,
            source = source,
            confidence = confidence.coerceIn(0f, 1f)
        )
    }

    private companion object {
        private const val SUPPORTED_CURRENCY = "EUR"
    }
}
