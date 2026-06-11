package it.danielebufarini.spesify.data.notifications

class RegexExpenseTextInterpreter(
    private val parser: ExpenseNotificationTextParser = ExpenseNotificationTextParser()
) : ExpenseTextInterpreter {

    override suspend fun interpret(text: String): ExpenseTextInterpretation =
        interpretAll(text).firstOrNull()
            ?: emptyExpenseTextInterpretation(InterpretationSource.Regex)

    override suspend fun interpretAll(text: String): List<ExpenseTextInterpretation> =
        parser.parseAll(text).map { parsed ->
            val merchant = parsed.merchant?.trim()?.takeIf { it.isNotBlank() }
            ExpenseTextInterpretation(
                amountMinor = parsed.amountMinor,
                merchant = merchant,
                currency = parsed.currency,
                confidence = if (merchant == null) AMOUNT_ONLY_CONFIDENCE else HIGH_CONFIDENCE,
                source = InterpretationSource.Regex
            )
        }

    private companion object {
        private const val HIGH_CONFIDENCE = 0.95f
        private const val AMOUNT_ONLY_CONFIDENCE = 0.60f
    }
}
