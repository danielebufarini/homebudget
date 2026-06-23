package it.danielebufarini.spesify.data.notifications

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidLocalLlmExpenseTextInterpreterTest {
    @Test
    fun promptConstrainModelToProvidedMoneyCandidates() {
        val prompt = buildAndroidExpenseLlmPrompt(
            notificationText = """
                Fineco
                ieri, 22:35
                Autorizzato utilizzo Carta ***0031
                Importo: 44,99 EUR, per: Amazon.it. Info:
                0228992899
            """.trimIndent(),
            moneyCandidates = listOf(
                MoneyCandidate(
                    amountMinor = 4_499L,
                    currency = "EUR",
                    originalText = "44,99 EUR",
                    startIndex = 74,
                    endIndex = 83
                )
            )
        )

        assertContains(prompt, "Choose selectedAmountMinor from the allowed candidates only.")
        assertContains(prompt, "Do not parse, convert, or invent another amount.")
        assertContains(prompt, "Ignore dates, times, card suffixes, phone numbers, balances, and percentages.")
        assertContains(prompt, "selectedAmountMinor=4499, currency=EUR, text=\"44,99 EUR\"")
        assertContains(prompt, "Autorizzato utilizzo Carta ***0031")
        assertContains(prompt, "0228992899")
        assertFalse(prompt.contains("Example:"))
        assertFalse(prompt.contains("SUPERMERCATO TEST"))
    }

    @Test
    fun promptIsBlankWithoutMoneyCandidates() {
        assertTrue(
            buildAndroidExpenseLlmPrompt(
                notificationText = "Pagamento carta 12,34 EUR",
                moneyCandidates = emptyList()
            ).isBlank()
        )
    }
}
