package it.danielebufarini.spesify.data.notifications

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import platform.Foundation.NSLock
import kotlin.coroutines.resume

/**
 * Swift-facing, callback-based provider used only by the iOS registry.
 *
 * This intentionally does not expose the shared suspend LocalExpenseTextLlmInterpreter
 * interface to Swift. Kotlin/Native exports suspend interfaces with both async and
 * completion-handler entry points, and Swift can map them to colliding Objective-C
 * selectors. The shared pipeline still depends on LocalExpenseTextLlmInterpreter;
 * this provider is only a small iOS adapter boundary.
 */
interface IosLocalLlmExpenseTextRequest {
    fun cancel()
}

interface IosLocalLlmExpenseTextProvider {
    fun interpret(
        text: String,
        moneyCandidates: List<MoneyCandidate>,
        completion: (String) -> Unit
    ): IosLocalLlmExpenseTextRequest
}

/**
 * Registry used by the native iOS layer to provide the current on-device model
 * implementation without letting Swift bypass the shared Kotlin pipeline.
 *
 * The installed provider uses a callback-style bridge instead of requiring a
 * Swift class to conform directly to the exported Kotlin suspend interface.
 * This avoids Swift/Objective-C selector conflicts generated for suspend
 * functions while keeping the real pipeline and validation in commonMain.
 */
object IosLocalLlmExpenseTextInterpreterRegistry {
    private val configurationLock = NSLock()
    private var enabled = true
    var isEnabled: Boolean
        get() = configurationLock.locked { enabled }
        set(value) { configurationLock.locked { enabled = value } }

    private var provider: IosLocalLlmExpenseTextProvider? = null

    fun installProvider(provider: IosLocalLlmExpenseTextProvider?) {
        configurationLock.locked { this.provider = provider }
    }

    internal suspend fun interpret(
        text: String,
        moneyCandidates: List<MoneyCandidate>
    ): String {
        if (moneyCandidates.isEmpty()) return ""
        val installedProvider = configurationLock.locked { if (enabled) provider else null } ?: return ""

        return withTimeoutOrNull(IOS_LOCAL_LLM_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { continuation ->
                val request = IosLlmRequestState { continuation.resume(it) }
                continuation.invokeOnCancellation { request.cancel() }
                if (!continuation.isActive) return@suspendCancellableCoroutine
                try {
                    request.attach(installedProvider.interpret(text, moneyCandidates, request::complete))
                } catch (cancelled: CancellationException) {
                    request.cancel()
                    continuation.cancel(cancelled)
                } catch (_: Throwable) {
                    request.complete("")
                }
            }
        }.orEmpty()
    }
}

/** Handles callback-before-return and cancellation-before-handle without calling foreign code under a lock. */
private class IosLlmRequestState(completion: (String) -> Unit) {
    private val lock = NSLock()
    private var terminal = false
    private var cancelled = false
    private var completion: ((String) -> Unit)? = completion
    private var request: IosLocalLlmExpenseTextRequest? = null

    fun attach(handle: IosLocalLlmExpenseTextRequest) {
        val mustCancel = lock.locked {
            if (!terminal) request = handle
            cancelled
        }
        if (mustCancel) handle.cancel()
    }

    fun complete(json: String) {
        val callback = lock.locked {
            if (terminal) return@locked null
            terminal = true
            request = null
            completion.also { completion = null }
        }
        callback?.invoke(json)
    }

    fun cancel() {
        val handle = lock.locked {
            if (terminal) return@locked null
            terminal = true
            cancelled = true
            completion = null
            request.also { request = null }
        }
        handle?.cancel()
    }
}

private inline fun <T> NSLock.locked(block: () -> T): T {
    lock()
    return try { block() } finally { unlock() }
}

/**
 * iOS DI adapter used by the shared interpretation pipeline.
 *
 * The concrete Apple Foundation Models/Core ML implementation is installed by
 * the native iOS app through [IosLocalLlmExpenseTextInterpreterRegistry]. If no
 * implementation is available, reaches its local deadline, or fails ordinarily,
 * this adapter returns an empty result for regex/manual-editor fallback.
 * Caller cancellation propagates and cancels the owned native request.
 */
class IosLocalLlmExpenseTextInterpreter : LocalExpenseTextLlmInterpreter {
    override suspend fun interpret(text: String): String =
        interpret(text = text, moneyCandidates = emptyList())

    override suspend fun interpret(
        text: String,
        moneyCandidates: List<MoneyCandidate>
    ): String = IosLocalLlmExpenseTextInterpreterRegistry.interpret(text, moneyCandidates)
}

private const val IOS_LOCAL_LLM_TIMEOUT_MILLIS = 25_000L
