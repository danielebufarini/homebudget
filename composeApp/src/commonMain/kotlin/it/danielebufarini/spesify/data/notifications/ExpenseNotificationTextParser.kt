package it.danielebufarini.spesify.data.notifications

data class ParsedExpenseNotification(
    val amountMinor: Long,
    val merchant: String?
)

class ExpenseNotificationTextParser {

    fun parse(rawText: String): ParsedExpenseNotification? {
        val amountMatch = amountRegex.find(rawText) ?: return null
        val amountMinor = amountMatch.toMinorUnitsOrNull() ?: return null
        return ParsedExpenseNotification(
            amountMinor = amountMinor,
            merchant = extractMerchant(rawText)
        )
    }

    private fun MatchResult.toMinorUnitsOrNull(): Long? {
        val integerPart = groups[1]?.value ?: return null
        val decimalPart = groups[2]?.value ?: return null
        val euros = integerPart.replace(".", "").toLongOrNull() ?: return null
        val cents = decimalPart.toLongOrNull() ?: return null
        return euros.safeMultiply(100L)?.safeAdd(cents)?.takeIf { it > 0L }
    }

    private fun Long.safeMultiply(other: Long): Long? =
        if (this > Long.MAX_VALUE / other) null else this * other

    private fun Long.safeAdd(other: Long): Long? =
        if (this > Long.MAX_VALUE - other) null else this + other

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
