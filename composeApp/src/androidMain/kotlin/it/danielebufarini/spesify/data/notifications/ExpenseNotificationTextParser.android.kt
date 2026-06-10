package it.danielebufarini.spesify.data.notifications

import java.math.BigDecimal

data class ParsedExpenseNotification(
    val amount: BigDecimal,
    val merchant: String?,
    val rawText: String
)

class ExpenseNotificationTextParser {

    fun parse(rawText: String): ParsedExpenseNotification? {
        val amountMatch = amountRegex.find(rawText) ?: return null
        val amount = amountMatch.toBigDecimalOrNull() ?: return null
        return ParsedExpenseNotification(
            amount = amount,
            merchant = extractMerchant(rawText),
            rawText = rawText
        )
    }

    private fun MatchResult.toBigDecimalOrNull(): BigDecimal? {
        val integerPart = groups[1]?.value ?: return null
        val decimalPart = groups[2]?.value ?: return null
        val normalized = integerPart.replace(".", "") + "." + decimalPart
        return runCatching { BigDecimal(normalized) }.getOrNull()
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
            .trimEnd('.', ',', ';', ':', '!', '?')
            .takeIf { it.isNotBlank() }
    }

    private companion object {
        private val amountRegex = Regex(
            pattern = "(?i)(?<![\\d.])(?:€\\s*|EUR\\s*)?(\\d{1,3}(?:\\.\\d{3})*|\\d+),(\\d{2})(?:\\s*(?:€|EUR))?(?!\\d)"
        )
        private val merchantMarkers = listOf(
            Regex(pattern = "(?i)\\bda\\s+([^\\n\\r.!?]+)"),
            Regex(pattern = "(?i)\\bpresso\\s+([^\\n\\r.!?]+)")
        )
    }
}
