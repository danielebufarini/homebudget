package it.danielebufarini.spesify.data

import kotlinx.coroutines.CancellationException

/** Throwable results for operation failures; cancellation still belongs to the caller's Job. */
internal inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Throwable) {
    Result.failure(failure)
}
