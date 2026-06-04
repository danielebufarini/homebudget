package it.danielebufarini.homebudget.ui.screens

import it.danielebufarini.homebudget.data.evaluateAmountExpressionInput
import it.danielebufarini.homebudget.data.formatAmountInput

class IosAmountExpressionEvaluator {
    fun formattedPositiveResult(expression: String): String? {
        return evaluateAmountExpressionInput(expression)
            ?.takeIf { amount -> amount > 0L }
            ?.let(::formatAmountInput)
    }
}
