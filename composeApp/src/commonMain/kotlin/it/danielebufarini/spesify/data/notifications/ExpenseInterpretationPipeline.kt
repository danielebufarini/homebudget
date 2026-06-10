package it.danielebufarini.spesify.data.notifications

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

data class ExpenseInterpretationPipelineConfig(
    val highConfidenceThreshold: Float = 0.85f,
    val llmConfidenceThreshold: Float = 0.75f,
    val llmTimeoutMillis: Long = 4_500L
)

class ExpenseInterpretationPipeline(
    private val regexInterpreter: ExpenseTextInterpreter = RegexExpenseTextInterpreter(),
    private val localLlmInterpreter: ExpenseTextInterpreter? = null,
    private val config: ExpenseInterpretationPipelineConfig = ExpenseInterpretationPipelineConfig()
) : ExpenseTextInterpreter {

    override suspend fun interpret(text: String): ExpenseTextInterpretation =
        // The generic entry point remains regex-only because common callers do not know
        // whether the notification source is whitelisted. Android notification detection
        // calls the overload below after whitelist validation.
        interpret(text = text, canUseLocalLlmFallback = false)
            ?: emptyExpenseTextInterpretation(InterpretationSource.Regex)

    suspend fun interpret(
        text: String,
        canUseLocalLlmFallback: Boolean
    ): ExpenseTextInterpretation? {
        val regexResult = regexInterpreter.interpret(text).normalized()
        if (!shouldFallback(regexResult)) return regexResult

        val llmResult = if (canUseLocalLlmFallback) {
            interpretWithLocalLlm(text)
        } else {
            null
        }

        return llmResult ?: regexResult.takeIf { it.hasValidAmount }
    }

    private fun shouldFallback(regexResult: ExpenseTextInterpretation): Boolean {
        if (!regexResult.hasValidAmount) return true
        if (regexResult.confidence < config.highConfidenceThreshold) return true
        if (regexResult.merchant.isNullOrBlank()) return true
        return false
    }

    private suspend fun interpretWithLocalLlm(text: String): ExpenseTextInterpretation? {
        val interpreter = localLlmInterpreter ?: return null
        return try {
            withTimeout(config.llmTimeoutMillis) {
                interpreter.interpret(text)
                    .normalized()
                    .takeIf { it.source == InterpretationSource.LocalLlm }
                    ?.takeIf { it.hasValidAmount }
                    ?.takeIf { it.confidence >= config.llmConfidenceThreshold }
                    ?.takeIf { it.currency == null || it.currency == SUPPORTED_CURRENCY }
            }
        } catch (_: TimeoutCancellationException) {
            null
        } catch (_: Throwable) {
            null
        }
    }

    private fun ExpenseTextInterpretation.normalized(): ExpenseTextInterpretation {
        val normalizedMerchant = merchant?.trim()?.takeIf { it.isNotBlank() }
        val normalizedCurrency = currency?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
        return copy(
            amountMinor = amountMinor?.takeIf { it > 0L },
            merchant = normalizedMerchant,
            currency = normalizedCurrency,
            confidence = confidence.coerceIn(0f, 1f)
        )
    }

    private companion object {
        private const val SUPPORTED_CURRENCY = "EUR"
    }
}
