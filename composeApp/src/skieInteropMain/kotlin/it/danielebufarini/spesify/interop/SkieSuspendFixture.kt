package it.danielebufarini.spesify.interop

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job

class SkieSuspendFixture {
    private val entered = CompletableDeferred<Unit>()
    private val cleaned = CompletableDeferred<Unit>()
    private val producer = MutableStateFlow<Job?>(null)
    val didCleanUp: Boolean get() = cleaned.isCompleted

    suspend fun read(): String {
        producer.value = currentCoroutineContext().job
        entered.complete(Unit)
        try {
            awaitCancellation()
        } finally {
            cleaned.complete(Unit)
        }
    }

    suspend fun awaitEntered() { entered.await() }
    suspend fun awaitCleanup() { cleaned.await() }
    fun cancelFromKotlin() { producer.value?.cancel() }
    suspend fun completedRead(): String = "completed"
}
