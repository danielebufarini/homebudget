package it.danielebufarini.spesify.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

internal class PersistentWriteScope(
    coroutineContext: CoroutineContext = Dispatchers.Default
) {
    private val scope = CoroutineScope(coroutineContext + SupervisorJob())

    fun launchWrite(
        onFailure: (Throwable) -> Unit = {},
        block: suspend () -> Unit
    ): Job = scope.launch {
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            onFailure(throwable)
        }
    }
}
