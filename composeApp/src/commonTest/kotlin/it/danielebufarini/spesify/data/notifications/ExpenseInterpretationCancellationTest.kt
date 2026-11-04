package it.danielebufarini.spesify.data.notifications

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ExpenseInterpretationCancellationTest {
    @Test
    fun cancellationThrownByProvider_isNotReturnedAsRegexFallback() = runTest {
        val cancellation = CancellationException("provider cancelled")
        val pipeline = pipeline { throw cancellation }
        val thrown = assertFailsWith<CancellationException> { interpret(pipeline) }
        assertEquals(cancellation.message, thrown.message)
        assertTrue(generateSequence<Throwable>(thrown) { it.cause }.any { it === cancellation })
    }

    @Test
    fun nestedProviderTimeout_isNotMistakenForPipelineDeadline() = runTest {
        val pipeline = pipeline { withTimeout(10) { awaitCancellation() } }
        assertFailsWith<CancellationException> { interpret(pipeline) }
    }

    @Test
    fun callerTimeout_propagatesThroughNonCancellableCleanup() = runTest {
        var returnedFallback = false
        var cleanedUp = false
        val entered = CompletableDeferred<Unit>()
        val pipeline = pipeline {
            entered.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) { delay(1); cleanedUp = true }
            }
        }
        assertFailsWith<CancellationException> {
            withTimeout(10) { interpret(pipeline); returnedFallback = true }
        }
        assertTrue(entered.isCompleted)
        assertTrue(cleanedUp)
        assertEquals(false, returnedFallback)
    }

    @Test
    fun pipelineDeadline_keepsRegexFallbackAndCancelsProvider() = runTest {
        var cleanedUp = false
        val pipeline = pipeline(timeout = 10) {
            try { awaitCancellation() } finally { cleanedUp = true }
        }
        val result = interpret(pipeline)
        assertEquals(InterpretationSource.Regex, result?.source)
        assertEquals(1234L, result?.amountMinor)
        assertTrue(cleanedUp)
    }

    private fun pipeline(timeout: Long = 1000, operation: suspend () -> ExpenseLlmExtraction): ExpenseInterpretationPipeline =
        ExpenseInterpretationPipeline(
            llmInterpreter = object : ExpenseTextLlmInterpreter {
                override suspend fun interpret(text: String, moneyCandidates: List<MoneyCandidate>): ExpenseLlmExtraction = operation()
            },
            config = ExpenseInterpretationPipelineConfig(llmTimeoutMillis = timeout)
        )

    private suspend fun interpret(pipeline: ExpenseInterpretationPipeline) = pipeline.interpret(
        text = "Pagamento carta 12,34 EUR", canUseLocalLlmFallback = true
    )
}
