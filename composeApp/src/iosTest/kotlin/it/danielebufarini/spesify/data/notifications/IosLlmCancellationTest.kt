package it.danielebufarini.spesify.data.notifications

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class IosLlmCancellationTest {
    @AfterTest
    fun resetRegistry() {
        IosLocalLlmExpenseTextInterpreterRegistry.installProvider(null)
        IosLocalLlmExpenseTextInterpreterRegistry.isEnabled = true
    }

    @Test
    fun providerCancellation_isNotAnEmptySuccessfulResponse() = runTest {
        val cancellation = CancellationException("provider cancelled")
        IosLocalLlmExpenseTextInterpreterRegistry.installProvider(object : IosLocalLlmExpenseTextProvider {
            override fun interpret(text: String, moneyCandidates: List<MoneyCandidate>, completion: (String) -> Unit): IosLocalLlmExpenseTextRequest {
                throw cancellation
            }
        })
        assertSame(cancellation, assertFailsWith<CancellationException> { interpret() })
    }

    @Test
    fun synchronousResponse_isNotReplacedByAnExceptionAfterCompletion() = runTest {
        IosLocalLlmExpenseTextInterpreterRegistry.installProvider(object : IosLocalLlmExpenseTextProvider {
            override fun interpret(text: String, moneyCandidates: List<MoneyCandidate>, completion: (String) -> Unit): IosLocalLlmExpenseTextRequest {
                completion("first")
                error("provider failed after completion")
            }
        })
        assertEquals("first", interpret())
    }

    private suspend fun interpret() = IosLocalLlmExpenseTextInterpreterRegistry.interpret(
        "payment", listOf(MoneyCandidate(1234L, "EUR", "12.34 EUR", 0, 9))
    )
}
