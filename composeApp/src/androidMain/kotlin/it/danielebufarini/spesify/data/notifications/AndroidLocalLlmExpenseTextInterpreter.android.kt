package it.danielebufarini.spesify.data.notifications

import android.util.Log
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel

class AndroidLocalLlmExpenseTextInterpreter(
    private val modelProvider: () -> GenerativeModel = { Generation.getClient() }
) : LocalExpenseTextLlmInterpreter {

    override suspend fun interpret(text: String): String =
        interpret(text = text, moneyCandidates = emptyList())

    override suspend fun interpret(
        text: String,
        moneyCandidates: List<MoneyCandidate>
    ): String {
        if (moneyCandidates.isEmpty()) return ""

        val model = modelProvider()
        val status = runCatching { model.checkStatus() }
            .onFailure { Log.w(TAG, "Local LLM status check failed", it) }
            .getOrNull()

        if (status != FeatureStatus.AVAILABLE) {
            Log.d(TAG, "Local LLM fallback unavailable: status=$status")
            return ""
        }

        return runCatching {
            model.generateContent(
                buildAndroidExpenseLlmPrompt(
                    notificationText = text,
                    moneyCandidates = moneyCandidates
                )
            ).candidates.firstOrNull()?.text.orEmpty()
        }.onFailure {
            Log.w(TAG, "Local LLM fallback failed", it)
        }.getOrNull().orEmpty()
    }

    private companion object {
        private const val TAG = "SpesifyLocalLlm"
    }
}

internal fun buildAndroidExpenseLlmPrompt(
    notificationText: String,
    moneyCandidates: List<MoneyCandidate>
): String {
    if (moneyCandidates.isEmpty()) return ""

    val candidates = moneyCandidates.joinToString(separator = "\n") { candidate ->
        "- selectedAmountMinor=${candidate.amountMinor}, currency=${candidate.currency ?: "null"}, text=\"${candidate.originalText}\""
    }

    return """
        You classify one bank or card notification for Spesify.

        Return only valid JSON. No Markdown. No explanation.

        Output shape:
        {
          "isExpense": true,
          "selectedAmountMinor": null,
          "currency": "EUR",
          "merchant": null,
          "confidence": 0.0
        }

        Rules:
        - Choose selectedAmountMinor from the allowed candidates only.
        - Do not parse, convert, or invent another amount.
        - Return {"isExpense":false,"selectedAmountMinor":null,"currency":null,"merchant":null,"confidence":0.0} if uncertain.
        - Return no expense for refunds, incoming transfers, salary, top-ups, or balance-only notifications.
        - Ignore dates, times, card suffixes, phone numbers, balances, and percentages.
        - merchant is the shop, company, payee, or merchant name. Use null if missing.

        Allowed amount candidates:
        $candidates

        Notification text:
        ${notificationText.trim()}
    """.trimIndent()
}
