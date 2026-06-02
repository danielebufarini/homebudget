package it.danielebufarini.homebudget.data

import it.danielebufarini.homebudget.widget.HomeBudgetWidgetRefresh
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

internal const val DEFAULT_WIDGET_REFRESH_COALESCE_MILLIS = 250L

class WidgetRefreshCoordinator(
    coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.Default,
    private val debounceMillis: Long = DEFAULT_WIDGET_REFRESH_COALESCE_MILLIS,
    private val refreshAction: () -> Unit = { HomeBudgetWidgetRefresh.requestRefresh() }
) {
    private val scope = CoroutineScope(coroutineContext)
    private val commands = Channel<Command>(capacity = Channel.UNLIMITED)

    init {
        scope.launch {
            var nextRefreshToken = 0L
            var pendingRefreshToken: Long? = null

            suspend fun scheduleRefresh() {
                if (pendingRefreshToken != null) {
                    return
                }

                val refreshToken = ++nextRefreshToken
                pendingRefreshToken = refreshToken
                launch {
                    delay(debounceMillis)
                    commands.send(Command.ExecuteScheduledRefresh(refreshToken))
                }
            }

            for (command in commands) {
                when (command) {
                    Command.RequestRefresh -> scheduleRefresh()
                    is Command.ExecuteScheduledRefresh -> {
                        if (pendingRefreshToken == command.refreshToken) {
                            pendingRefreshToken = null
                            refreshAction()
                        }
                    }
                    is Command.RefreshNow -> {
                        pendingRefreshToken = null
                        refreshAction()
                        command.completion.complete(Unit)
                    }
                }
            }
        }
    }

    fun requestRefresh() {
        commands.trySend(Command.RequestRefresh)
    }

    suspend fun refreshNow() {
        val completion = CompletableDeferred<Unit>()
        commands.send(Command.RefreshNow(completion))
        completion.await()
    }

    private sealed interface Command {
        data object RequestRefresh : Command
        data class ExecuteScheduledRefresh(val refreshToken: Long) : Command
        data class RefreshNow(val completion: CompletableDeferred<Unit>) : Command
    }
}
