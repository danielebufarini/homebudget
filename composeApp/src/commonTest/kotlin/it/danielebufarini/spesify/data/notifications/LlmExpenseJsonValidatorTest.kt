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
            """{"isExpense":true,"amountMinor":1234,"currency":"eur","merchant":"  Shop  ","confidence":0.90}"""
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
            """{"isExpense":true,"amountMinor":1234,"merchant":"Shop","confidence":0.90}"""
        )

        assertEquals(1_234L, result?.amountMinor)
        assertEquals("EUR", result?.currency)
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
}
