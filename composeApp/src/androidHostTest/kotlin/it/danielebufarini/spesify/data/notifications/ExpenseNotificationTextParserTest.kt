package it.danielebufarini.spesify.data.notifications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ExpenseNotificationTextParserTest {
    private val parser = ExpenseNotificationTextParser()

    @Test
    fun parse_extractsAmountWithCommaDecimals() {
        val parsed = parser.parse("Hai speso 12,50€")

        assertEquals(1_250L, parsed?.amountMinor)
    }

    @Test
    fun parse_extractsAmountWithEuroSymbolAfterNumber() {
        val parsed = parser.parse("Hai speso 12,50 €")

        assertEquals(1_250L, parsed?.amountMinor)
    }

    @Test
    fun parse_extractsAmountWithEuroSymbolBeforeNumber() {
        val parsed = parser.parse("Spesa di €12,50 da Esselunga")

        assertEquals(1_250L, parsed?.amountMinor)
    }

    @Test
    fun parse_extractsAmountWithEurPrefix() {
        val parsed = parser.parse("Pagamento autorizzato: EUR 12,50")

        assertEquals(1_250L, parsed?.amountMinor)
    }

    @Test
    fun parse_extractsAmountWithEurSuffix() {
        val parsed = parser.parse("Pagamento di 12,50 EUR presso Esselunga")

        assertEquals(1_250L, parsed?.amountMinor)
    }

    @Test
    fun parse_extractsAmountWithThousandsSeparator() {
        val parsed = parser.parse("Pagamento di 1.234,56 €")

        assertEquals(123_456L, parsed?.amountMinor)
    }

    @Test
    fun parse_extractsMerchantAfterDa() {
        val parsed = parser.parse("Hai speso 12,50€ da Esselunga")

        assertEquals("Esselunga", parsed?.merchant)
    }

    @Test
    fun parse_extractsMerchantAfterPresso() {
        val parsed = parser.parse("Pagamento di 12,50 EUR presso Esselunga")

        assertEquals("Esselunga", parsed?.merchant)
    }

    @Test
    fun parse_returnsNullMerchantWhenMissing() {
        val parsed = parser.parse("Pagamento carta 12,50€")

        assertEquals(null, parsed?.merchant)
    }

    @Test
    fun parse_returnsNullWhenAmountIsMissing() {
        assertNull(parser.parse("Nuovo messaggio dalla banca"))
    }

    @Test
    fun parse_doesNotKeepRawNotificationTextInResult() {
        val rawText = "Pagamento carta 12,34 EUR presso SUPERMERCATO TEST"
        val parsed = parser.parse(rawText)

        assertEquals(1_234L, parsed?.amountMinor)
        assertFalse(parsed.toString().contains(rawText))
    }
}
