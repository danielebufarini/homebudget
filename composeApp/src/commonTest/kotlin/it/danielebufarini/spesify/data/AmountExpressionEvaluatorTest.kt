package it.danielebufarini.spesify.data

import kotlin.test.Test
import kotlin.test.assertEquals

class AmountExpressionEvaluatorTest {

    @Test
    fun evaluateAmountExpressionInput_keepsPlainAmountBehavior() {
        assertEquals(1250L, evaluateAmountExpressionInput("12.50"))
        assertEquals(1250L, evaluateAmountExpressionInput("12,50"))
    }

    @Test
    fun evaluateAmountExpressionInput_appliesOperatorPrecedence() {
        assertEquals(1850L, evaluateAmountExpressionInput("12.50 + 3 * 2"))
    }

    @Test
    fun evaluateAmountExpressionInput_supportsParentheses() {
        assertEquals(1480L, evaluateAmountExpressionInput("12.50 + 8.30 - (3 * 2)"))
    }

    @Test
    fun evaluateAmountExpressionInput_supportsCalculatorOperatorSymbols() {
        assertEquals(900L, evaluateAmountExpressionInput("12 ÷ 2 + 3 × 1"))
    }

    @Test
    fun evaluateAmountExpressionInput_roundsFinalResultToMinorUnits() {
        assertEquals(333L, evaluateAmountExpressionInput("10 / 3"))
    }

    @Test
    fun evaluateAmountExpressionInput_rejectsInvalidOrIncompleteExpressions() {
        assertEquals(null, evaluateAmountExpressionInput(""))
        assertEquals(null, evaluateAmountExpressionInput("12 +"))
        assertEquals(null, evaluateAmountExpressionInput("(12 + 3"))
        assertEquals(null, evaluateAmountExpressionInput("12..3"))
        assertEquals(null, evaluateAmountExpressionInput("12 abc"))
    }

    @Test
    fun evaluateAmountExpressionInput_rejectsDivisionByZeroAndOverflow() {
        assertEquals(null, evaluateAmountExpressionInput("12 / 0"))
        assertEquals(null, evaluateAmountExpressionInput("92233720368547760 * 100"))
    }
}
