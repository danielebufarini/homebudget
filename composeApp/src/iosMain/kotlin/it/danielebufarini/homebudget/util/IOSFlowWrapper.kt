package it.danielebufarini.homebudget.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

/**
 * Keeps iOS-facing Flow collection on a KMP background dispatcher while delivering
 * callbacks on the main dispatcher. Swift closures created from @MainActor view
 * models inherit main-actor isolation and will trap if invoked from a background
 * queue before they can hop back to MainActor themselves.
 */
internal class IOSFlowWrapper<T>(
    private val sourceFlow: Flow<T>
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun subscribe(
        onEach: (T) -> Unit,
        onError: (Throwable) -> Unit = {}
    ): IOSCancellable {
        val job: Job = sourceFlow
            .onEach { data ->
                withContext(Dispatchers.Main) {
                    onEach(data)
                }
            }
            .catch { cause ->
                withContext(Dispatchers.Main) {
                    onError(cause)
                }
            }
            .launchIn(scope)

        return IOSCancellable { job.cancel() }
    }

    fun cancel() {
        scope.cancel()
    }
}

internal fun interface IOSCancellable {
    fun cancel()
}
