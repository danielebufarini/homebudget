package it.danielebufarini.spesify.data.notifications

import android.util.Log
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel

class AndroidLocalLlmExpenseTextInterpreter(
    private val modelProvider: () -> GenerativeModel = { Generation.getClient() }
) : LocalExpenseTextLlmInterpreter {

    override suspend fun interpret(text: String): String {
        val model = modelProvider()
        val status = runCatching { model.checkStatus() }
            .onFailure { Log.w(TAG, "Local LLM status check failed", it) }
            .getOrNull()

        if (status != FeatureStatus.AVAILABLE) {
            Log.d(TAG, "Local LLM fallback unavailable: status=$status")
            return ""
        }

        return runCatching {
            model.generateContent(buildPrompt(text)).candidates.firstOrNull()?.text.orEmpty()
        }.onFailure {
            Log.w(TAG, "Local LLM fallback failed", it)
        }.getOrNull().orEmpty()
    }

    private fun buildPrompt(notificationText: String): String = """
        You extract expense data from one bank or card notification.
        
        Return only valid JSON. No Markdown. No explanation.
        
        Output shape:
        {
          "transactions": [
            {
              "isExpense": true,
              "amountMinor": 0,
              "currency": "EUR",
              "merchant": null,
              "confidence": 0.0
            }
          ]
        }
        
        Rules:
        - Return {"transactions":[]} if the notification is not clearly a payment made by the user.
        - Extract only monetary amounts, not dates, times, percentages, card numbers, or balances.
        - Convert the paid amount to cents. Example: 12,34 EUR means 1234.
        - Use "EUR" only when the text indicates euro.
        - merchant is the shop, company, payee, or merchant name. Use null if missing.
        - Do not guess.
        
        Notification text:
        ${notificationText.trim()}
    """.trimIndent()

    private companion object {
        private const val TAG = "SpesifyLocalLlm"
    }
}
