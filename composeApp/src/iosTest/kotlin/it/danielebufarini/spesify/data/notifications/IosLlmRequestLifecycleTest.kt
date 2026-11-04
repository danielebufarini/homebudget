package it.danielebufarini.spesify.data.notifications

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import platform.Foundation.NSLock
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IosLlmRequestLifecycleTest {
    @AfterTest
    fun reset() { IosLocalLlmExpenseTextInterpreterRegistry.installProvider(null) }

    @Test
    fun localDeadline_cancelsTheRequestAndIgnoresLateCallbacks() = runTest {
        val handle = RecordingLlmRequest()
        var callback: ((String) -> Unit)? = null
        install { completion -> callback = completion; handle }
        assertEquals("", interpret())
        assertEquals(1, handle.cancelCount)
        val complete = checkNotNull(callback)
        complete("late")
        complete("late-again")
        assertEquals(1, handle.cancelCount)
    }

    @Test
    fun callerDeadline_cancelsSwiftWorkWithoutReturningFallback() = runTest {
        val handle = RecordingLlmRequest()
        install { handle }
        assertFailsWith<CancellationException> { withTimeout(10) { interpret() } }
        assertEquals(1, handle.cancelCount)
    }

    @Test
    fun cancellationBeforeProviderReturns_stillCancelsTheReturnedHandle() = runTest {
        val handle = RecordingLlmRequest()
        val operation = async(start = CoroutineStart.LAZY) { interpret() }
        install { operation.cancel(); handle }
        operation.start()
        assertFailsWith<CancellationException> { operation.await() }
        assertEquals(1, handle.cancelCount)
    }

    @Test
    fun synchronousResponse_beforeHandleAttachment_completesOnceWithoutCancelling() = runTest {
        val handle = RecordingLlmRequest()
        install { completion -> completion("first"); completion("second"); handle }
        assertEquals("first", interpret())
        assertEquals(0, handle.cancelCount)
    }

    @Test
    fun concurrentCallbacks_andLateCompletion_doNotResumeTwice() = runTest {
        val entered = CompletableDeferred<(String) -> Unit>()
        val handle = RecordingLlmRequest()
        install { completion -> entered.complete(completion); handle }
        // Keep the bridge deadline on the same real dispatcher as concurrent callbacks.
        // A virtual test deadline can otherwise advance before native worker threads run.
        val operation = async(Dispatchers.Default) { interpret() }
        val callback = entered.await()
        (0..31).map { index -> async(Dispatchers.Default) { callback("result-$index") } }.awaitAll()
        assertTrue(operation.await().startsWith("result-"))
        callback("late")
        assertEquals(0, handle.cancelCount)
    }

    private fun install(start: ((String) -> Unit) -> IosLocalLlmExpenseTextRequest) {
        IosLocalLlmExpenseTextInterpreterRegistry.installProvider(object : IosLocalLlmExpenseTextProvider {
            override fun interpret(text: String, moneyCandidates: List<MoneyCandidate>, completion: (String) -> Unit) = start(completion)
        })
    }
    private suspend fun interpret() = IosLocalLlmExpenseTextInterpreterRegistry.interpret(
        "payment", listOf(MoneyCandidate(1234L, "EUR", "12.34 EUR", 0, 9))
    )
}

private class RecordingLlmRequest : IosLocalLlmExpenseTextRequest {
    private val lock = NSLock()
    private var count = 0
    val cancelCount: Int get() { lock.lock(); return try { count } finally { lock.unlock() } }
    override fun cancel() { lock.lock(); try { count++ } finally { lock.unlock() } }
}
