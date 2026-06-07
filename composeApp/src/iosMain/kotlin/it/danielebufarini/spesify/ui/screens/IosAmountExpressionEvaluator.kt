package it.danielebufarini.spesify.ui.screens

import it.danielebufarini.spesify.data.evaluateAmountExpressionInput
import it.danielebufarini.spesify.data.formatAmountInput

class IosAmountExpressionEvaluator {
    fun formattedPositiveResult(expression: String): String? {
        return evaluateAmountExpressionInput(expression)
            ?.takeIf { amount -> amount > 0L }
            ?.let(::formatAmountInput)
    }
}
