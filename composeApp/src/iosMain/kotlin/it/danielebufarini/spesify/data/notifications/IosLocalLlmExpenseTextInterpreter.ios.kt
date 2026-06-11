package it.danielebufarini.spesify.data.notifications

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
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
interface IosLocalLlmExpenseTextProvider {
    fun interpret(text: String, completion: (String) -> Unit)
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
    var isEnabled: Boolean = true

    private var provider: IosLocalLlmExpenseTextProvider? = null

    fun installProvider(provider: IosLocalLlmExpenseTextProvider?) {
        this.provider = provider
    }

    internal suspend fun interpret(text: String): String {
        if (!isEnabled) return ""
        val installedProvider = provider ?: return ""

        return withTimeoutOrNull(IOS_LOCAL_LLM_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { continuation ->
                try {
                    installedProvider.interpret(text) { json ->
                        if (continuation.isActive) {
                            continuation.resume(json)
                        }
                    }
                } catch (_: Throwable) {
                    if (continuation.isActive) {
                        continuation.resume("")
                    }
                }
            }
        }.orEmpty()
    }
}

/**
 * iOS DI adapter used by the shared interpretation pipeline.
 *
 * The concrete Apple Foundation Models/Core ML implementation is installed by
 * the native iOS app through [IosLocalLlmExpenseTextInterpreterRegistry]. If no
 * implementation is available, times out, or fails, this adapter returns an
 * empty result and the pipeline falls back gracefully to regex/manual-editor
 * behavior.
 */
class IosLocalLlmExpenseTextInterpreter : LocalExpenseTextLlmInterpreter {
    override suspend fun interpret(text: String): String =
        IosLocalLlmExpenseTextInterpreterRegistry.interpret(text)
}

private const val IOS_LOCAL_LLM_TIMEOUT_MILLIS = 10_000L
