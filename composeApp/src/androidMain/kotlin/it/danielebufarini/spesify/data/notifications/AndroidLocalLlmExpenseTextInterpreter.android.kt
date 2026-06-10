package it.danielebufarini.spesify.data.notifications

import android.util.Log
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel

class AndroidLocalLlmExpenseTextInterpreter(
    private val validator: LlmExpenseJsonValidator,
    private val modelProvider: () -> GenerativeModel = { Generation.getClient() }
) : ExpenseTextInterpreter {

    override suspend fun interpret(text: String): ExpenseTextInterpretation {
        val model = modelProvider()
        val status = runCatching { model.checkStatus() }
            .onFailure { Log.w(TAG, "Local LLM status check failed", it) }
            .getOrNull()

        if (status != FeatureStatus.AVAILABLE) {
            Log.d(TAG, "Local LLM fallback unavailable: status=$status")
            return emptyExpenseTextInterpretation(InterpretationSource.LocalLlm)
        }

        val rawJson = runCatching {
            model.generateContent(buildPrompt(text)).candidates.firstOrNull()?.text.orEmpty()
        }.onFailure {
            Log.w(TAG, "Local LLM fallback failed", it)
        }.getOrNull() ?: return emptyExpenseTextInterpretation(InterpretationSource.LocalLlm)

        return validator.validate(rawJson)
            ?: emptyExpenseTextInterpretation(InterpretationSource.LocalLlm)
    }

    private fun buildPrompt(notificationText: String): String = """
        You interpret one bank/card notification for a personal finance app.
        The text was read locally on device. Return strict JSON only, with no Markdown.
        Use this exact shape:
        {"isExpense":true,"amount":"12.34","currency":"EUR","merchant":"SUPERMERCATO TEST","confidence":0.86}

        Rules:
        - isExpense must be true only for card payments, debit card payments, POS purchases, online purchases, or other money spent by the user.
        - Return isExpense false if the text is not an expense, is a refund, is an incoming transfer, is an income, or is ambiguous.
        - amount must be a positive decimal string using dot as decimal separator.
        - currency must be EUR when present.
        - merchant may be null if unavailable.
        - confidence must be between 0 and 1.
        - Do not include any field not shown in the JSON shape.

        Notification text:
        ${notificationText.trim()}
    """.trimIndent()

    private companion object {
        private const val TAG = "SpesifyLocalLlm"
    }
}
