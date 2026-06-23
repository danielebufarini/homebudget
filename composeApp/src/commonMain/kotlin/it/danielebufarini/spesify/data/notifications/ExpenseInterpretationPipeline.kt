package it.danielebufarini.spesify.data.notifications

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

data class ExpenseInterpretationPipelineConfig(
    val highConfidenceThreshold: Float = 0.85f,
    val llmConfidenceThreshold: Float = 0.75f,
    val llmTimeoutMillis: Long = 15_000L
)

class ExpenseInterpretationPipeline(
    private val regexInterpreter: ExpenseTextInterpreter = RegexExpenseTextInterpreter(),
    private val localLlmInterpreter: LocalExpenseTextLlmInterpreter? = null,
    private val llmJsonValidator: LlmExpenseJsonValidator = LlmExpenseJsonValidator(),
    private val config: ExpenseInterpretationPipelineConfig = ExpenseInterpretationPipelineConfig(),
    private val evidenceParser: ExpenseNotificationTextParser = ExpenseNotificationTextParser()
) : ExpenseTextInterpreter {

    override suspend fun interpret(text: String): ExpenseTextInterpretation =
        // The generic entry point remains regex-only because common callers do not know
        // whether local fallback is appropriate. Platform flows call the overload below.
        interpretAll(text = text, canUseLocalLlmFallback = false).firstOrNull()
            ?: emptyExpenseTextInterpretation(InterpretationSource.Regex)

    override suspend fun interpretAll(text: String): List<ExpenseTextInterpretation> =
        interpretAll(text = text, canUseLocalLlmFallback = false)

    suspend fun interpret(
        text: String,
        canUseLocalLlmFallback: Boolean
    ): ExpenseTextInterpretation? = interpretAll(
        text = text,
        canUseLocalLlmFallback = canUseLocalLlmFallback
    ).firstOrNull()

    suspend fun interpretAll(
        text: String,
        canUseLocalLlmFallback: Boolean
    ): List<ExpenseTextInterpretation> {
        val input = text.trim()
        if (input.isBlank()) return emptyList()

        val regexResults = regexInterpreter.interpretAll(input)
            .map { it.normalized() }
            .filter { it.hasValidAmount }
            .distinctBy { it.amountMinor to it.merchant.orEmpty().uppercase() }

        if (!shouldFallback(regexResults, input)) return regexResults

        val llmResults = if (canUseLocalLlmFallback) {
            interpretAllWithLocalLlm(input)
        } else {
            emptyList()
        }

        return when {
            llmResults.isEmpty() -> regexResults
            regexResults.isEmpty() -> llmResults
            llmResults.size >= regexResults.size -> llmResults
            else -> regexResults
        }
    }

    private fun shouldFallback(regexResults: List<ExpenseTextInterpretation>, text: String): Boolean {
        if (regexResults.isEmpty()) return true
        if (regexResults.any { !it.hasValidAmount }) return true
        if (regexResults.any { it.confidence < config.highConfidenceThreshold }) return true
        if (regexResults.any { it.merchant.isNullOrBlank() }) return true

        val monetaryAmountCount = evidenceParser.monetaryAmountMinorEvidence(text).size
        if (monetaryAmountCount > regexResults.size) return true

        return false
    }

    private suspend fun interpretAllWithLocalLlm(text: String): List<ExpenseTextInterpretation> {
        val interpreter = localLlmInterpreter ?: return emptyList()
        return try {
            withTimeout(config.llmTimeoutMillis) {
                val rawJson = interpreter.interpret(text).trim()
                if (rawJson.isBlank()) return@withTimeout emptyList()
                llmJsonValidator.validateAll(
                    rawJson = rawJson,
                    minConfidence = config.llmConfidenceThreshold,
                    ocrText = text
                ).map { it.normalized() }
                    .filter { it.source == InterpretationSource.LocalLlm }
                    .filter { it.hasValidAmount }
                    .filter { it.confidence >= config.llmConfidenceThreshold }
                    .filter { it.currency == SUPPORTED_CURRENCY }
                    .distinctBy { it.amountMinor to it.merchant.orEmpty().uppercase() }
            }
        } catch (_: TimeoutCancellationException) {
            emptyList()
        } catch (_: Throwable) {
            emptyList()
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
