package it.danielebufarini.homebudget.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

internal class PersistentWriteScope(
    coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.Default
) {
    private val scope = CoroutineScope(coroutineContext)

    fun launch(block: suspend CoroutineScope.() -> Unit): Job = scope.launch(block = block)
}
