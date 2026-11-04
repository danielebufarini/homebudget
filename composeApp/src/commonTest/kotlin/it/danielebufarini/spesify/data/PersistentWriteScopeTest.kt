package it.danielebufarini.spesify.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PersistentWriteScopeTest {
    @Test
    fun closingUiOwner_doesNotCancelAnAcceptedApplicationWrite() = runTest {
        val uiJob = Job()
        val uiScope = CoroutineScope(coroutineContext + uiJob)
        val writes = PersistentWriteScope(coroutineContext)
        val accepted = CompletableDeferred<Job>()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var persisted = false
        uiScope.launch {
            accepted.complete(writes.launchWrite {
                entered.complete(Unit)
                release.await()
                persisted = true
            })
            awaitCancellation()
        }
        val write = accepted.await()
        entered.await()
        uiJob.cancelAndJoin()
        assertFalse(write.isCancelled)
        release.complete(Unit)
        write.join()
        assertTrue(persisted)
    }

    @Test
    fun writeCancellation_isNotReportedAsAnOrdinaryPersistenceFailure() = runTest {
        var reportedFailure = false
        val write = PersistentWriteScope(coroutineContext).launchWrite(onFailure = { reportedFailure = true }) {
            throw CancellationException("write cancelled")
        }
        write.join()
        assertTrue(write.isCancelled)
        assertFalse(reportedFailure)
    }
}
