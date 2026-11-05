package it.danielebufarini.spesify.ui.screens

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface IosGroupedExpensesState {
    data class Data(val snapshot: IosGroupedExpensesSnapshot) : IosGroupedExpensesState
    data object Failed : IosGroupedExpensesState
}

sealed interface IosMonthlyIncomesState {
    data class Data(val snapshot: IosMonthlyIncomesSnapshot) : IosMonthlyIncomesState
    data object Failed : IosMonthlyIncomesState
}

sealed interface IosRecurringExpensesState {
    data class Data(val snapshot: IosRecurringExpensesSnapshot) : IosRecurringExpensesState
    data object Failed : IosRecurringExpensesState
}

sealed interface IosCategoriesState {
    data class Data(val snapshot: IosCategoriesSnapshot) : IosCategoriesState
    data object Failed : IosCategoriesState
}

// Keep the entire cold read (including source creation, bootstrap and mapping) inside
// the boundary. Ordinary exceptions cannot safely escape a SKIE Flow into Swift.
internal fun <T, S> iosReadStates(
    source: () -> Flow<T>,
    data: (T) -> S,
    failed: S,
    collectionMutex: Mutex = Mutex()
): Flow<S> = flow {
    // SKIE can finish Swift iteration before Kotlin's cancellation cleanup ends.
    // A replacement collection must wait for the preceding source's finally.
    collectionMutex.withLock {
        emitAll(source().map(data))
    }
}.catch { failure ->
    if (failure is CancellationException) throw failure
    currentCoroutineContext().ensureActive()
    emit(failed)
}.flowOn(Dispatchers.Default)
