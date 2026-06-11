package it.danielebufarini.spesify.data.notifications

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ExpenseInterpretationPipelineTest {

    @Test
    fun highConfidenceRegexResultSkipsLocalLlmFallback() = runTest {
        val localLlm = CountingLocalLlmInterpreter(validLocalLlmJson())
        val pipeline = pipeline(localLlm = localLlm)

        val result = pipeline.interpret(
            text = "Pagamento carta 12,34 EUR presso SUPERMERCATO TEST",
            canUseLocalLlmFallback = true
        )

        assertNotNull(result)
        assertEquals(1_234L, result.amountMinor)
        assertEquals("SUPERMERCATO TEST", result.merchant)
        assertEquals(InterpretationSource.Regex, result.source)
        assertEquals(0, localLlm.calls)
    }

    @Test
    fun highConfidenceRegexExtractsMultiplePaymentBlocksAndSkipsLocalLlmFallback() = runTest {
        val localLlm = CountingLocalLlmInterpreter(validLocalLlmJson())
        val pipeline = pipeline(localLlm = localLlm)

        val results = pipeline.interpretAll(
            text = """
                Pagamento eseguito
                È stata richiesta l'autorizzazione al pagamento di 22,20 EUR presso MI CASA TOASTERIA con la tua carta illimity ***6593.
                Pagamento eseguito
                È stata richiesta l'autorizzazione al pagamento di 15,20 EUR presso DR MAX ITALIA - MONZA con la tua carta illimity ***6593.
            """.trimIndent(),
            canUseLocalLlmFallback = true
        )

        assertEquals(2, results.size)
        assertEquals(2_220L, results[0].amountMinor)
        assertEquals("MI CASA TOASTERIA", results[0].merchant)
        assertEquals(1_520L, results[1].amountMinor)
        assertEquals("DR MAX ITALIA - MONZA", results[1].merchant)
        assertEquals(InterpretationSource.Regex, results[0].source)
        assertEquals(InterpretationSource.Regex, results[1].source)
        assertEquals(0, localLlm.calls)
    }

    @Test
    fun amountOnlyRegexResultTriggersLocalLlmFallbackWhenEnabled() = runTest {
        val localLlm = CountingLocalLlmInterpreter(validLocalLlmJson(merchant = "SUPERMERCATO TEST"))
        val pipeline = pipeline(localLlm = localLlm)

        val result = pipeline.interpret(
            text = "Pagamento carta 12,34 EUR",
            canUseLocalLlmFallback = true
        )

        assertNotNull(result)
        assertEquals(1_234L, result.amountMinor)
        assertEquals("SUPERMERCATO TEST", result.merchant)
        assertEquals(InterpretationSource.LocalLlm, result.source)
        assertEquals(1, localLlm.calls)
    }

    @Test
    fun localLlmFallbackIsNotUsedWhenDisabled() = runTest {
        val localLlm = CountingLocalLlmInterpreter(validLocalLlmJson(merchant = "SUPERMERCATO TEST"))
        val pipeline = pipeline(localLlm = localLlm)

        val result = pipeline.interpret(
            text = "Pagamento carta 12,34 EUR",
            canUseLocalLlmFallback = false
        )

        assertNotNull(result)
        assertEquals(1_234L, result.amountMinor)
        assertEquals(null, result.merchant)
        assertEquals(InterpretationSource.Regex, result.source)
        assertEquals(0, localLlm.calls)
    }

    @Test
    fun regexFailureTriggersLocalLlmFallbackWhenEnabledAndAvailable() = runTest {
        val localLlm = CountingLocalLlmInterpreter(validLocalLlmJson(amountMinor = 999L, merchant = "BAR CENTRALE"))
        val pipeline = pipeline(localLlm = localLlm)

        val result = pipeline.interpret(
            text = "Carta usata al BAR CENTRALE per 9,99 EUR",
            canUseLocalLlmFallback = true
        )

        assertNotNull(result)
        assertEquals(999L, result.amountMinor)
        assertEquals("BAR CENTRALE", result.merchant)
        assertEquals(InterpretationSource.LocalLlm, result.source)
        assertEquals(1, localLlm.calls)
    }

    @Test
    fun invalidLlmJsonIsRejected() = runTest {
        val pipeline = pipeline(localLlm = CountingLocalLlmInterpreter("not json"))

        val result = pipeline.interpret(
            text = "Carta usata al BAR CENTRALE per nove euro e novantanove centesimi",
            canUseLocalLlmFallback = true
        )

        assertNull(result)
    }

    @Test
    fun localLlmFailureDoesNotCrashTheFlow() = runTest {
        val pipeline = pipeline(localLlm = ThrowingLocalLlmInterpreter)

        val result = pipeline.interpret(
            text = "Pagamento carta 12,34 EUR",
            canUseLocalLlmFallback = true
        )

        assertNotNull(result)
        assertEquals(1_234L, result.amountMinor)
        assertEquals(InterpretationSource.Regex, result.source)
    }

    @Test
    fun validLocalLlmJsonBecomesCandidateTransaction() = runTest {
        val useCase = InterpretExpenseTextUseCase(
            pipeline(localLlm = CountingLocalLlmInterpreter(validLocalLlmJson(amountMinor = 1_999L, merchant = "BAR CENTRALE")))
        )

        val candidate = useCase.execute(
            rawText = "Carta usata al BAR CENTRALE per 19,99 EUR",
            canUseLocalLlmFallback = true
        )

        assertNotNull(candidate)
        assertEquals(1_999L, candidate.amountMinor)
        assertEquals("BAR CENTRALE", candidate.merchant)
        assertEquals("EUR", candidate.currency)
        assertEquals(InterpretationSource.LocalLlm, candidate.source)
        assertFalse(candidate.toString().contains("Carta usata"))
    }

    @Test
    fun validLocalLlmArrayBecomesMultipleCandidateTransactions() = runTest {
        val useCase = InterpretExpenseTextUseCase(
            pipeline(
                localLlm = CountingLocalLlmInterpreter(
                    validLocalLlmTransactionsJson(
                        LlmFixture(2_220L, "MI CASA TOASTERIA"),
                        LlmFixture(1_520L, "DR MAX ITALIA - MONZA")
                    )
                )
            )
        )

        val candidates = useCase.executeAll(
            rawText = "Pagamento non standard 22,20 EUR MI CASA TOASTERIA\nPagamento non standard 15,20 EUR DR MAX ITALIA - MONZA",
            canUseLocalLlmFallback = true
        )

        assertEquals(2, candidates.size)
        assertEquals(2_220L, candidates[0].amountMinor)
        assertEquals("MI CASA TOASTERIA", candidates[0].merchant)
        assertEquals(1_520L, candidates[1].amountMinor)
        assertEquals("DR MAX ITALIA - MONZA", candidates[1].merchant)
    }

    @Test
    fun localLlmAmountNotPresentAsMonetaryEvidenceIsRejected() = runTest {
        val pipeline = pipeline(localLlm = CountingLocalLlmInterpreter(validLocalLlmJson(amountMinor = 2_700L, merchant = "STATUS BAR")))

        val result = pipeline.interpret(
            text = "22:13\n27\nPagamento eseguito 22,20 EUR presso MI CASA TOASTERIA",
            canUseLocalLlmFallback = true
        )

        assertNotNull(result)
        assertEquals(2_220L, result.amountMinor)
        assertEquals(InterpretationSource.Regex, result.source)
    }

    @Test
    fun sharedUseCaseMapsHighConfidenceRegexResultIntoCandidate() = runTest {
        val useCase = InterpretExpenseTextUseCase(
            ExpenseInterpretationPipeline(regexInterpreter = RegexExpenseTextInterpreter())
        )

        val candidate = useCase.execute(
            rawText = "Pagamento carta 12,34 EUR presso SUPERMERCATO TEST"
        )

        assertNotNull(candidate)
        assertEquals(1_234L, candidate.amountMinor)
        assertEquals("SUPERMERCATO TEST", candidate.merchant)
        assertEquals("SUPERMERCATO TEST", candidate.description)
        assertEquals("EUR", candidate.currency)
        assertEquals(InterpretationSource.Regex, candidate.source)
    }

    @Test
    fun sharedUseCaseRejectsUnsupportedCurrencyAndDoesNotExposeRawText() = runTest {
        val useCase = InterpretExpenseTextUseCase(
            ExpenseInterpretationPipeline(regexInterpreter = RegexExpenseTextInterpreter())
        )
        val rawText = "Pagamento carta 12,34 EUR presso SUPERMERCATO TEST"

        val candidate = useCase.execute(rawText = rawText)

        assertNotNull(candidate)
        assertFalse(candidate.toString().contains(rawText))
        assertNull(useCase.execute(rawText = "Pagamento carta 12,34 USD presso SUPERMERCATO TEST"))
    }

    private fun pipeline(
        localLlm: LocalExpenseTextLlmInterpreter
    ): ExpenseInterpretationPipeline = ExpenseInterpretationPipeline(
        regexInterpreter = RegexExpenseTextInterpreter(),
        localLlmInterpreter = localLlm,
        llmJsonValidator = LlmExpenseJsonValidator(),
        config = ExpenseInterpretationPipelineConfig(llmTimeoutMillis = 1_000L)
    )

    private fun validLocalLlmJson(
        amountMinor: Long = 1_234L,
        merchant: String? = "SUPERMERCATO TEST"
    ): String {
        val merchantValue = merchant?.let { "\"$it\"" } ?: "null"
        return """{"isExpense":true,"amountMinor":$amountMinor,"currency":"EUR","merchant":$merchantValue,"confidence":0.86}"""
    }

    private fun validLocalLlmTransactionsJson(vararg fixtures: LlmFixture): String {
        val transactions = fixtures.joinToString(",") { fixture ->
            """{"isExpense":true,"amountMinor":${fixture.amountMinor},"currency":"EUR","merchant":"${fixture.merchant}","confidence":0.86}"""
        }
        return """{"transactions":[$transactions]}"""
    }

    private data class LlmFixture(
        val amountMinor: Long,
        val merchant: String
    )

    private class CountingLocalLlmInterpreter(
        private val result: String
    ) : LocalExpenseTextLlmInterpreter {
        var calls: Int = 0
            private set

        override suspend fun interpret(text: String): String {
            calls += 1
            return result
        }
    }

    private object ThrowingLocalLlmInterpreter : LocalExpenseTextLlmInterpreter {
        override suspend fun interpret(text: String): String = error("local model failed")
    }
}
