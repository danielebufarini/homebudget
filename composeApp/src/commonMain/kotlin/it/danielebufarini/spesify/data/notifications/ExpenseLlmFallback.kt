package it.danielebufarini.spesify.data.notifications

data class MoneyCandidate(
    val amountMinor: Long,
    val currency: String?,
    val originalText: String,
    val startIndex: Int,
    val endIndex: Int
)

data class ExpenseLlmExtraction(
    val isExpense: Boolean,
    val selectedAmountMinor: Long?,
    val currency: String?,
    val merchant: String?,
    val confidence: Float
)

interface ExpenseTextLlmInterpreter {
    suspend fun interpret(
        text: String,
        moneyCandidates: List<MoneyCandidate>
    ): ExpenseLlmExtraction?

    suspend fun interpretAll(
        text: String,
        moneyCandidates: List<MoneyCandidate>
    ): List<ExpenseLlmExtraction> =
        interpret(text, moneyCandidates)?.let(::listOf).orEmpty()
}

class JsonExpenseTextLlmInterpreter(
    private val rawInterpreter: LocalExpenseTextLlmInterpreter,
    private val jsonValidator: LlmExpenseJsonValidator = LlmExpenseJsonValidator()
) : ExpenseTextLlmInterpreter {

    override suspend fun interpret(
        text: String,
        moneyCandidates: List<MoneyCandidate>
    ): ExpenseLlmExtraction? = interpretAll(text, moneyCandidates).firstOrNull()

    override suspend fun interpretAll(
        text: String,
        moneyCandidates: List<MoneyCandidate>
    ): List<ExpenseLlmExtraction> {
        if (moneyCandidates.isEmpty()) return emptyList()
        val rawJson = rawInterpreter.interpret(text, moneyCandidates).trim()
        if (rawJson.isBlank()) return emptyList()
        return jsonValidator.decodeAll(rawJson)
    }
}

class ExpenseLlmExtractionValidator {
    fun validate(
        extraction: ExpenseLlmExtraction,
        moneyCandidates: List<MoneyCandidate>,
        sourceText: String,
        minConfidence: Float = DEFAULT_MIN_CONFIDENCE
    ): ExpenseTextInterpretation? = validateAll(
        extractions = listOf(extraction),
        moneyCandidates = moneyCandidates,
        sourceText = sourceText,
        minConfidence = minConfidence
    ).firstOrNull()

    fun validateAll(
        extractions: List<ExpenseLlmExtraction>,
        moneyCandidates: List<MoneyCandidate>,
        sourceText: String,
        minConfidence: Float = DEFAULT_MIN_CONFIDENCE
    ): List<ExpenseTextInterpretation> {
        if (moneyCandidates.isEmpty()) return emptyList()
        if (ExpenseTextSafetyClassifier.isRejectedForExpense(sourceText)) return emptyList()

        return extractions
            .mapNotNull { extraction ->
                extraction.toInterpretation(
                    moneyCandidates = moneyCandidates,
                    minConfidence = minConfidence
                )
            }
            .distinctBy { it.amountMinor to it.merchant.orEmpty().uppercase() }
    }

    private fun ExpenseLlmExtraction.toInterpretation(
        moneyCandidates: List<MoneyCandidate>,
        minConfidence: Float
    ): ExpenseTextInterpretation? {
        if (!isExpense) return null

        val amountMinor = selectedAmountMinor?.takeIf { it > 0L } ?: return null
        val selectedCandidates = moneyCandidates.filter { it.amountMinor == amountMinor }
        if (selectedCandidates.isEmpty()) return null

        val normalizedCurrency = currency
            ?.trim()
            ?.uppercase()
            ?.takeIf { it.isNotBlank() }
            ?: SUPPORTED_CURRENCY
        if (normalizedCurrency != SUPPORTED_CURRENCY) return null
        if (selectedCandidates.none { it.currency == null || it.currency == normalizedCurrency }) return null

        if (confidence !in 0f..1f) return null
        if (confidence < minConfidence) return null

        return ExpenseTextInterpretation(
            amountMinor = amountMinor,
            merchant = merchant?.trim()?.takeIf { it.isNotBlank() },
            currency = normalizedCurrency,
            confidence = confidence,
            source = InterpretationSource.LocalLlm
        )
    }

    private companion object {
        private const val SUPPORTED_CURRENCY = "EUR"
        private const val DEFAULT_MIN_CONFIDENCE = 0.75f
    }
}

internal object ExpenseTextSafetyClassifier {
    fun isRejectedForExpense(text: String): Boolean {
        val normalized = text.replace(Regex("\\s+"), " ").trim()
        if (normalized.isBlank()) return false

        if (rejectedEventPatterns.any { it.containsMatchIn(normalized) }) return true
        return balanceOnlyPattern.containsMatchIn(normalized) &&
            !expenseSignalPattern.containsMatchIn(normalized)
    }

    private val rejectedEventPatterns = listOf(
        Regex("(?i)\\b(?:rimborso|rimborsato|rimborsata|storno|riaccredito|refund|refunded|cashback)\\b"),
        Regex("(?i)\\b(?:bonifico\\s+ricevuto|hai\\s+ricevuto|accredito|incoming\\s+transfer|transfer\\s+received)\\b"),
        Regex("(?i)\\b(?:stipendio|salary|pensione|income)\\b"),
        Regex("(?i)\\b(?:top[ -]?up|ricarica\\s+(?:conto|wallet|carta|prepagata))\\b")
    )
    private val balanceOnlyPattern = Regex("(?i)\\b(?:saldo|balance|disponibilit(?:a|à))\\b")
    private val expenseSignalPattern = Regex(
        "(?i)\\b(?:pagamento|payment|acquisto|purchase|autorizzato|autorizzazione|utilizzo\\s+carta|carta\\s+usata|pos|addebit[oi])\\b"
    )
}
