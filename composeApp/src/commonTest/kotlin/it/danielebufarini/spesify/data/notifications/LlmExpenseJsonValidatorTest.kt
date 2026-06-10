package it.danielebufarini.spesify.data.notifications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
                """{"isExpense":false,"amount":"12.34","currency":"EUR","merchant":"Shop","confidence":0.90}"""
            )
        )
    }

    @Test
    fun negativeAmountIsRejected() {
        assertNull(
            validator.validate(
                """{"isExpense":true,"amount":"-12.34","currency":"EUR","merchant":"Shop","confidence":0.90}"""
            )
        )
    }

    @Test
    fun zeroAmountIsRejected() {
        assertNull(
            validator.validate(
                """{"isExpense":true,"amount":"0.00","currency":"EUR","merchant":"Shop","confidence":0.90}"""
            )
        )
    }

    @Test
    fun unsupportedCurrencyIsRejected() {
        assertNull(
            validator.validate(
                """{"isExpense":true,"amount":"12.34","currency":"USD","merchant":"Shop","confidence":0.90}"""
            )
        )
    }

    @Test
    fun lowConfidenceIsRejected() {
        assertNull(
            validator.validate(
                """{"isExpense":true,"amount":"12.34","currency":"EUR","merchant":"Shop","confidence":0.20}"""
            )
        )
    }

    @Test
    fun unknownJsonFieldsAreRejected() {
        assertNull(
            validator.validate(
                """{"isExpense":true,"amount":"12.34","currency":"EUR","merchant":"Shop","confidence":0.90,"rawText":"do not keep me"}"""
            )
        )
    }

    @Test
    fun validJsonIsNormalizedIntoExpenseTextInterpretation() {
        val result = validator.validate(
            """{"isExpense":true,"amount":"12.34","currency":"eur","merchant":"  Shop  ","confidence":0.90}"""
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
            """{"isExpense":true,"amount":"12.34","merchant":"Shop","confidence":0.90}"""
        )

        assertEquals(1_234L, result?.amountMinor)
        assertEquals("EUR", result?.currency)
    }
}
