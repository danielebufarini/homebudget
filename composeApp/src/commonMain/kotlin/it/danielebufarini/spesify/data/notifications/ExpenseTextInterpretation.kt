package it.danielebufarini.spesify.data.notifications

interface ExpenseTextInterpreter {
    suspend fun interpret(text: String): ExpenseTextInterpretation

    suspend fun interpretAll(text: String): List<ExpenseTextInterpretation> {
        val interpretation = interpret(text)
        return if (interpretation.hasValidAmount) listOf(interpretation) else emptyList()
    }
}

data class ExpenseTextInterpretation(
    val amountMinor: Long?,
    val merchant: String?,
    val currency: String?,
    val confidence: Float,
    val source: InterpretationSource
) {
    val hasValidAmount: Boolean get() = amountMinor != null && amountMinor > 0L
}

enum class InterpretationSource {
    Regex,
    LocalLlm
}

internal fun emptyExpenseTextInterpretation(source: InterpretationSource): ExpenseTextInterpretation =
    ExpenseTextInterpretation(
        amountMinor = null,
        merchant = null,
        currency = null,
        confidence = 0f,
        source = source
    )
