package it.danielebufarini.spesify.ui.screens

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class IosReadStatesTest {
    private val snapshot = IosGroupedExpensesSnapshot("12.34", "empty", emptyList())

    @Test
    fun sourceCreationAndTransformationFailuresBecomeStates() = runTest {
        val creation = IosGroupedExpensesObserver(ExpenseGroupingMode.ByCategory) { error("bootstrap") }
        assertEquals(listOf(IosGroupedExpensesState.Failed), creation.states.toList())
        val transformation = IosGroupedExpensesObserver(ExpenseGroupingMode.ByCategory) {
            flowOf(snapshot).map { error("localization or mapping") }
        }
        assertEquals(listOf(IosGroupedExpensesState.Failed), transformation.states.toList())
    }

    @Test
    fun failureAfterDataPreservesTheSnapshotAndRetryRecollects() = runTest {
        var collections = 0
        val observer = IosGroupedExpensesObserver(ExpenseGroupingMode.ByCategory) {
            flow {
                collections += 1
                emit(snapshot)
                if (collections == 1) error("database read")
            }
        }
        val first = observer.states.toList()
        assertSame(snapshot, assertIs<IosGroupedExpensesState.Data>(first.first()).snapshot)
        assertEquals(IosGroupedExpensesState.Failed, first.last())
        assertEquals(1, collections)
        assertEquals(listOf(IosGroupedExpensesState.Data(snapshot)), observer.states.toList())
        assertEquals(2, collections)
    }

    @Test
    fun allFourObserversConvertOrdinaryFailures() = runTest {
        fun <T> broken(): Flow<T> = flow { error("read") }
        assertEquals(listOf(IosGroupedExpensesState.Failed), IosGroupedExpensesObserver(ExpenseGroupingMode.ByCategory) { broken() }.states.toList())
        assertEquals(listOf(IosMonthlyIncomesState.Failed), IosMonthlyIncomesObserver(ExpenseGroupingMode.ByDate) { broken() }.states.toList())
        assertEquals(listOf(IosRecurringExpensesState.Failed), IosRecurringExpensesObserver { broken() }.states.toList())
        assertEquals(listOf(IosCategoriesState.Failed), IosCategoriesController { broken() }.states().toList())
    }

    @Test
    fun cancellationIsRethrownAndNeverBecomesFailed() = runTest {
        val cancellation = CancellationException("Kotlin cancellation")
        val observer = IosGroupedExpensesObserver(ExpenseGroupingMode.ByCategory) { flow { throw cancellation } }
        assertSame(cancellation, assertFailsWith<CancellationException> { observer.states.toList() })
    }

    @Test
    fun inactiveContextCannotPublishAnOrdinaryFailure() = runTest {
        val observer = IosGroupedExpensesObserver(ExpenseGroupingMode.ByCategory) {
            flow {
                currentCoroutineContext().job.cancel()
                error("failure racing cancellation")
            }
        }
        val received = mutableListOf<IosGroupedExpensesState>()
        assertFailsWith<CancellationException> { observer.states.toList(received) }
        assertEquals(emptyList(), received)
    }
}
