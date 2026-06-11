package it.danielebufarini.spesify.data.notifications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExpenseNotificationTextParserSharedTest {
    private val parser = ExpenseNotificationTextParser()

    @Test
    fun parse_extractsKnownPaymentTextWithMerchant() {
        val parsed = parser.parse("Pagamento carta 12,34 EUR presso SUPERMERCATO TEST")

        assertEquals(1_234L, parsed?.amountMinor)
        assertEquals("SUPERMERCATO TEST", parsed?.merchant)
        assertEquals("EUR", parsed?.currency)
    }

    @Test
    fun parseAll_extractsMultipleStructuredPaymentBlocks() {
        val parsed = parser.parseAll(
            """
            Pagamento eseguito
            È stata richiesta l'autorizzazione al pagamento di 22,20 EUR presso MI CASA TOASTERIA con la tua carta illimity ***6593.
            Pagamento eseguito
            È stata richiesta l'autorizzazione al pagamento di 15,20 EUR presso DR MAX ITALIA - MONZA con la tua carta illimity ***6593.
            """.trimIndent()
        )

        assertEquals(2, parsed.size)
        assertEquals(2_220L, parsed[0].amountMinor)
        assertEquals("MI CASA TOASTERIA", parsed[0].merchant)
        assertEquals(1_520L, parsed[1].amountMinor)
        assertEquals("DR MAX ITALIA - MONZA", parsed[1].merchant)
    }

    @Test
    fun monetaryEvidence_ignoresStatusBarAndCardSuffixNumbers() {
        val evidence = parser.monetaryAmountMinorEvidence(
            """
            Thu Jun 11
            22:13
            27
            pagamento di 22,20 EUR presso MI CASA TOASTERIA con la tua carta illimity ***6593.
            pagamento di 15,20 EUR presso DR MAX ITALIA - MONZA con la tua carta illimity ***6593.
            """.trimIndent()
        )

        assertEquals(setOf(2_220L, 1_520L), evidence)
        assertTrue(2_700L !in evidence)
        assertTrue(659_300L !in evidence)
    }

    @Test
    fun parse_normalizesDotDecimalAndIntegerAmounts() {
        assertEquals(1_234L, parser.parse("Pagamento carta 12.34 EUR")?.amountMinor)
        assertEquals(1_000L, parser.parse("Pagamento carta 10 EUR")?.amountMinor)
        assertEquals(123_456L, parser.parse("Pagamento carta 1.234,56 EUR")?.amountMinor)
        assertEquals(123_456L, parser.parse("Pagamento carta 1234,56 EUR")?.amountMinor)
    }

    @Test
    fun parse_rejectsInvalidZeroNegativeAndUnsupportedCurrencyAmounts() {
        assertNull(parser.parse("Pagamento carta 0,00 EUR"))
        assertNull(parser.parse("Pagamento carta -12,34 EUR"))
        assertNull(parser.parse("Pagamento carta 12,345 EUR"))
        assertNull(parser.parse("Pagamento carta 12,34 USD"))
    }

    @Test
    fun parse_doesNotKeepRawTextInResult() {
        val rawText = "Pagamento carta 12,34 EUR presso SUPERMERCATO TEST"
        val parsed = parser.parse(rawText)

        assertEquals(1_234L, parsed?.amountMinor)
        assertFalse(parsed.toString().contains(rawText))
    }
}
