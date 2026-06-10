package it.danielebufarini.spesify.data.notifications

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ExpenseInterpretationPipelineTest {

    @Test
    fun highConfidenceRegexResultSkipsLocalLlmFallback() = runTest {
        val localLlm = CountingInterpreter(validLocalLlmResult())
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
    fun lowConfidenceRegexResultTriggersLocalLlmFallbackWhenWhitelisted() = runTest {
        val localLlm = CountingInterpreter(validLocalLlmResult(merchant = "SUPERMERCATO TEST"))
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
    fun localLlmFallbackIsNotUsedWhenSourcePackageIsNotWhitelisted() = runTest {
        val localLlm = CountingInterpreter(validLocalLlmResult(merchant = "SUPERMERCATO TEST"))
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
    fun regexFailureTriggersLocalLlmFallbackWhenWhitelisted() = runTest {
        val localLlm = CountingInterpreter(validLocalLlmResult(amountMinor = 999L, merchant = "BAR CENTRALE"))
        val pipeline = pipeline(localLlm = localLlm)

        val result = pipeline.interpret(
            text = "Carta usata al BAR CENTRALE per nove euro e novantanove centesimi",
            canUseLocalLlmFallback = true
        )

        assertNotNull(result)
        assertEquals(999L, result.amountMinor)
        assertEquals("BAR CENTRALE", result.merchant)
        assertEquals(InterpretationSource.LocalLlm, result.source)
        assertEquals(1, localLlm.calls)
    }

    private fun pipeline(
        localLlm: ExpenseTextInterpreter
    ): ExpenseInterpretationPipeline = ExpenseInterpretationPipeline(
        regexInterpreter = RegexExpenseTextInterpreter(),
        localLlmInterpreter = localLlm,
        config = ExpenseInterpretationPipelineConfig(llmTimeoutMillis = 1_000L)
    )

    private fun validLocalLlmResult(
        amountMinor: Long = 1_234L,
        merchant: String? = "SUPERMERCATO TEST"
    ): ExpenseTextInterpretation = ExpenseTextInterpretation(
        amountMinor = amountMinor,
        merchant = merchant,
        currency = "EUR",
        confidence = 0.86f,
        source = InterpretationSource.LocalLlm
    )

    private class CountingInterpreter(
        private val result: ExpenseTextInterpretation
    ) : ExpenseTextInterpreter {
        var calls: Int = 0
            private set

        override suspend fun interpret(text: String): ExpenseTextInterpretation {
            calls += 1
            return result
        }
    }
}
