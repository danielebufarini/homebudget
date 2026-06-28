package it.danielebufarini.spesify.data.notifications

import it.danielebufarini.spesify.data.parsePositiveLocalizedAmountMinorOrNull

private const val SUPPORTED_NOTIFICATION_CURRENCY = "EUR"

data class ParsedExpenseNotification(
    val amountMinor: Long,
    val merchant: String?,
    val currency: String? = SUPPORTED_NOTIFICATION_CURRENCY
)

class ExpenseNotificationTextParser {

    fun parse(rawText: String): ParsedExpenseNotification? = parseAll(rawText).firstOrNull()

    fun parseAll(rawText: String): List<ParsedExpenseNotification> {
        if (ExpenseTextSafetyClassifier.isRejectedForExpense(rawText)) return emptyList()

        val structured = sequenceOf(
            finecoPaymentRegex.findAll(rawText),
            structuredPaymentRegex.findAll(rawText)
        ).flatten()
            .mapNotNull { match -> match.toStructuredPayment() }
            .toList()
            .distinctBy { it.amountMinor to it.merchant.orEmpty().uppercase() }

        if (structured.isNotEmpty()) return structured

        val amountMatch = explicitMoneyRegex.find(rawText) ?: return emptyList()
        if (amountMatch.hasUnsupportedCurrency(rawText)) return emptyList()

        val amountText = amountMatch.amountGroupValue() ?: return emptyList()
        val amountMinor = parsePositiveLocalizedAmountMinorOrNull(amountText) ?: return emptyList()
        return listOf(
            ParsedExpenseNotification(
                amountMinor = amountMinor,
                merchant = extractMerchant(rawText),
                currency = SUPPORTED_NOTIFICATION_CURRENCY
            )
        )
    }

    fun monetaryAmountMinorEvidence(rawText: String): Set<Long> =
        moneyCandidates(rawText).map { it.amountMinor }.toSet()

    fun moneyCandidates(rawText: String): List<MoneyCandidate> =
        explicitMoneyRegex.findAll(rawText)
            .filterNot { it.hasUnsupportedCurrency(rawText) }
            .mapNotNull { match ->
                val amountMinor = match.amountGroupValue()
                    ?.let(::parsePositiveLocalizedAmountMinorOrNull)
                    ?: return@mapNotNull null
                MoneyCandidate(
                    amountMinor = amountMinor,
                    currency = SUPPORTED_NOTIFICATION_CURRENCY,
                    originalText = match.value.trim(),
                    startIndex = match.range.first,
                    endIndex = match.range.last + 1
                )
            }
            .distinctBy { it.amountMinor to it.startIndex to it.endIndex }
            .toList()

    private fun MatchResult.amountGroupValue(): String? = groups[1]?.value ?: groups[2]?.value

    private fun MatchResult.toStructuredPayment(): ParsedExpenseNotification? {
        val amountText = groups[1]?.value ?: return null
        val amountMinor = parsePositiveLocalizedAmountMinorOrNull(amountText) ?: return null
        val merchant = groups[2]
            ?.value
            ?.cleanMerchant()
        return ParsedExpenseNotification(
            amountMinor = amountMinor,
            merchant = merchant,
            currency = SUPPORTED_NOTIFICATION_CURRENCY
        )
    }

    private fun MatchResult.hasUnsupportedCurrency(rawText: String): Boolean {
        val contextStart = (range.first - CURRENCY_CONTEXT_CHARS).coerceAtLeast(0)
        val contextEnd = (range.last + 1 + CURRENCY_CONTEXT_CHARS).coerceAtMost(rawText.length)
        val context = rawText.substring(contextStart, contextEnd)
        return currencyRegex.findAll(context).any { match ->
            match.value.normalizeCurrencyToken() != SUPPORTED_NOTIFICATION_CURRENCY
        }
    }

    private fun String.normalizeCurrencyToken(): String = when (trim().uppercase()) {
        "€", "EURO", "EUROS", SUPPORTED_NOTIFICATION_CURRENCY -> SUPPORTED_NOTIFICATION_CURRENCY
        else -> trim().uppercase()
    }

    private fun extractMerchant(rawText: String): String? {
        return merchantMarkers.firstNotNullOfOrNull { marker ->
            marker.find(rawText)
                ?.groups
                ?.get(1)
                ?.value
                ?.cleanMerchant()
        }
    }

    private fun String.cleanMerchant(): String? {
        return trim()
            .replace(Regex("(?i)\\s+con\\s+(?:la\\s+tua|una|il)?\\s*carta\\b.*$"), "")
            .replace(Regex("(?i)\\s+with\\s+(?:your\\s+)?card\\b.*$"), "")
            .replace(Regex("(?i)\\s+using\\s+(?:your\\s+)?card\\b.*$"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimEnd('.', ',', ';', ':', '!', '?')
            .takeIf { it.isNotBlank() }
    }

    private companion object {
        private const val CURRENCY_CONTEXT_CHARS = 12
        private const val LOCALIZED_AMOUNT = "\\d+(?:[.,]\\d{3})*(?:[,.]\\d{1,2})?|\\d+"
        private val explicitMoneyRegex = Regex(
            pattern = "(?i)(?<![\\d.,+-])(?:€\\s*|EUR\\s*|EURO(?:S)?\\s*)($LOCALIZED_AMOUNT)(?:\\s*(?:€|EUR|EURO(?:S)?))?(?!\\d)|(?<![\\d.,+-])($LOCALIZED_AMOUNT)\\s*(?:€|EUR|EURO(?:S)?)(?!\\d)"
        )
        private val structuredPaymentRegex = Regex(
            pattern = "(?is)\\b(?:pagamento|payment)\\s+(?:di|of)\\s+($LOCALIZED_AMOUNT)\\s*(?:€|EUR|EURO(?:S)?)\\s+(?:presso|at)\\s+(.+?)(?=\\s+(?:con\\s+(?:la\\s+tua|una|il)?\\s*carta|with\\s+(?:your\\s+)?card|using\\s+(?:your\\s+)?card)\\b|\\n\\s*(?:Pagamento|Payment|È|E\\s+stata|Authorization|Autorizzazione)\\b|$)"
        )
        private val finecoPaymentRegex = Regex(
            pattern = "(?is)\\bImporto:\\s*($LOCALIZED_AMOUNT)\\s*(?:€|EUR|EURO(?:S)?)\\s*,?\\s*per:\\s*(.+?)(?=\\.\\s*Info:|\\s+Info:|\\n|$)"
        )
        private val currencyRegex = Regex(pattern = "(?i)\\b(?:EUR|EURO(?:S)?|USD|GBP|CHF|CAD|AUD|JPY|CNY|SEK|NOK|DKK)\\b|€")
        private val merchantMarkers = listOf(
            Regex(pattern = "(?i)\\bda\\s+([^\\n\\r.!?]+)"),
            Regex(pattern = "(?i)\\bpresso\\s+([^\\n\\r.!?]+)"),
            Regex(pattern = "(?i)\\bat\\s+([^\\n\\r.!?]+)")
        )
    }
}
