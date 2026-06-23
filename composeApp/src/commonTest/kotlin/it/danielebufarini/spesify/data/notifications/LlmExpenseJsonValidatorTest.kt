package it.danielebufarini.spesify.data.notifications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LlmExpenseJsonValidatorTest {
    private val validator = LlmExpenseJsonValidator()

    @Test
    fun invalidJsonIsRejected() {
        assertNull(validator.validate("not json"))
    }

    @Test
    fun nonExpenseIsRejected() {
        assertNull(
            validator.validate(
                """{"isExpense":false,"amountMinor":1234,"currency":"EUR","merchant":"Shop","confidence":0.90}"""
            )
        )
    }

    @Test
    fun missingAmountMinorIsRejected() {
        assertNull(
            validator.validate(
                """{"isExpense":true,"currency":"EUR","merchant":"Shop","confidence":0.90}"""
            )
        )
    }

    @Test
    fun negativeAmountMinorIsRejected() {
        assertNull(
            validator.validate(
                """{"isExpense":true,"amountMinor":-1234,"currency":"EUR","merchant":"Shop","confidence":0.90}"""
            )
        )
    }

    @Test
    fun zeroAmountMinorIsRejected() {
        assertNull(
            validator.validate(
                """{"isExpense":true,"amountMinor":0,"currency":"EUR","merchant":"Shop","confidence":0.90}"""
            )
        )
    }

    @Test
    fun unsupportedCurrencyIsRejected() {
        assertNull(
            validator.validate(
                """{"isExpense":true,"amountMinor":1234,"currency":"USD","merchant":"Shop","confidence":0.90}"""
            )
        )
    }

    @Test
    fun lowConfidenceIsRejected() {
        assertNull(
            validator.validate(
                """{"isExpense":true,"amountMinor":1234,"currency":"EUR","merchant":"Shop","confidence":0.20}"""
            )
        )
    }

    @Test
    fun unknownJsonFieldsAreRejected() {
        assertNull(
            validator.validate(
                """{"isExpense":true,"amountMinor":1234,"currency":"EUR","merchant":"Shop","confidence":0.90,"rawText":"do not keep me"}"""
            )
        )
    }

    @Test
    fun legacyAmountFieldIsRejectedBecauseAmountMinorIsRequired() {
        assertNull(
            validator.validate(
                """{"isExpense":true,"amount":"12.34","currency":"EUR","merchant":"Shop","confidence":0.90}"""
            )
        )
    }

    @Test
    fun validJsonIsNormalizedIntoExpenseTextInterpretation() {
        val result = validator.validate(
            """{"isExpense":true,"amountMinor":1234,"currency":"eur","merchant":"  Shop  ","confidence":0.90}""",
            ocrText = "Pagamento carta 12,34 EUR presso Shop"
        )

        assertEquals(1_234L, result?.amountMinor)
        assertEquals("EUR", result?.currency)
        assertEquals("Shop", result?.merchant)
        assertEquals(0.90f, result?.confidence)
        assertEquals(InterpretationSource.LocalLlm, result?.source)
    }

    @Test
    fun missingCurrencyIsSafelyInferredAsEur() {
        val result = validator.validate(
            """{"isExpense":true,"amountMinor":1234,"merchant":"Shop","confidence":0.90}""",
            ocrText = "Pagamento carta 12,34 EUR presso Shop"
        )

        assertEquals(1_234L, result?.amountMinor)
        assertEquals("EUR", result?.currency)
    }

    @Test
    fun selectedAmountMinorJsonFieldIsAccepted() {
        val result = validator.validate(
            """{"isExpense":true,"selectedAmountMinor":1234,"currency":"EUR","merchant":"Shop","confidence":0.90}""",
            ocrText = "Pagamento carta 12,34 EUR presso Shop"
        )

        assertEquals(1_234L, result?.amountMinor)
        assertEquals("Shop", result?.merchant)
    }

    @Test
    fun validTransactionsArrayIsNormalizedIntoMultipleInterpretations() {
        val results = validator.validateAll(
            """{"transactions":[{"isExpense":true,"amountMinor":2220,"currency":"EUR","merchant":"MI CASA TOASTERIA","confidence":0.91},{"isExpense":true,"amountMinor":1520,"currency":"EUR","merchant":"DR MAX ITALIA - MONZA","confidence":0.89}]}""",
            ocrText = "pagamento di 22,20 EUR presso MI CASA TOASTERIA\npagamento di 15,20 EUR presso DR MAX ITALIA - MONZA"
        )

        assertEquals(2, results.size)
        assertEquals(2_220L, results[0].amountMinor)
        assertEquals("MI CASA TOASTERIA", results[0].merchant)
        assertEquals(1_520L, results[1].amountMinor)
        assertEquals("DR MAX ITALIA - MONZA", results[1].merchant)
    }

    @Test
    fun copiedAmountTextCanProvideOcrEvidenceForMultipleTransactions() {
        val results = validator.validateAll(
            """{"transactions":[{"isExpense":true,"amountMinor":4499,"amountText":"44,99 EUR","merchant":"Amazon.it","confidence":1},{"isExpense":true,"amountMinor":16430,"amountText":"164,3 EUR","merchant":"TRENITALIA - LEFRECCE","confidence":1}]}""",
            ocrText = """
            Importo: 44,99 EUR, per: Amazon.it. Info:
            Importo: 164,3 EUR, per: TRENITALIA -
            LEFRECCE. Info:
            """.trimIndent()
        )

        assertEquals(2, results.size)
        assertEquals(4_499L, results[0].amountMinor)
        assertEquals("Amazon.it", results[0].merchant)
        assertEquals(16_430L, results[1].amountMinor)
        assertEquals("TRENITALIA - LEFRECCE", results[1].merchant)
    }

    @Test
    fun amountWithoutMonetaryEvidenceIsRejectedWhenOcrTextIsProvided() {
        val result = validator.validate(
            """{"isExpense":true,"amountMinor":2700,"currency":"EUR","merchant":"Battery","confidence":0.90}""",
            ocrText = "22:13\n27\npagamento di 22,20 EUR presso MI CASA TOASTERIA"
        )

        assertNull(result)
    }

    @Test
    fun transactionArrayFiltersUnsafeAmountsAgainstOcrMonetaryEvidence() {
        val results = validator.validateAll(
            """{"transactions":[{"isExpense":true,"amountMinor":2700,"currency":"EUR","merchant":"Battery","confidence":0.90},{"isExpense":true,"amountMinor":2220,"currency":"EUR","merchant":"MI CASA TOASTERIA","confidence":0.91}]}""",
            ocrText = "22:13\n27\npagamento di 22,20 EUR presso MI CASA TOASTERIA"
        )

        assertEquals(1, results.size)
        assertEquals(2_220L, results.single().amountMinor)
        assertTrue(results.single().merchant != "Battery")
    }

    @Test
    fun copiedAmountTextCannotBypassMissingMoneyCandidate() {
        val result = validator.validate(
            """{"isExpense":true,"amountMinor":31,"amountText":"0031","currency":"EUR","merchant":"Card suffix","confidence":0.90}""",
            ocrText = """
                Autorizzato utilizzo Carta ***0031
                Importo: 44,99 EUR, per: Amazon.it. Info:
                0228992899
            """.trimIndent()
        )

        assertNull(result)
    }

    @Test
    fun refundSourceTextIsRejectedEvenWhenJsonLooksValid() {
        val result = validator.validate(
            """{"isExpense":true,"amountMinor":1234,"currency":"EUR","merchant":"Shop","confidence":0.90}""",
            ocrText = "Rimborso carta 12,34 EUR da Shop"
        )

        assertNull(result)
    }
}
