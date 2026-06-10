package it.danielebufarini.spesify.data.notifications

class RegexExpenseTextInterpreter(
    private val parser: ExpenseNotificationTextParser = ExpenseNotificationTextParser()
) : ExpenseTextInterpreter {

    override suspend fun interpret(text: String): ExpenseTextInterpretation {
        val parsed = parser.parse(text) ?: return emptyExpenseTextInterpretation(InterpretationSource.Regex)
        val merchant = parsed.merchant?.trim()?.takeIf { it.isNotBlank() }
        return ExpenseTextInterpretation(
            amountMinor = parsed.amountMinor,
            merchant = merchant,
            currency = DEFAULT_CURRENCY,
            confidence = if (merchant == null) AMOUNT_ONLY_CONFIDENCE else HIGH_CONFIDENCE,
            source = InterpretationSource.Regex
        )
    }

    private companion object {
        private const val DEFAULT_CURRENCY = "EUR"
        private const val HIGH_CONFIDENCE = 0.95f
        private const val AMOUNT_ONLY_CONFIDENCE = 0.60f
    }
}
