package it.danielebufarini.spesify.data.notifications

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class DefaultAppWhitelistRepositoryTest {

    @Test
    fun refreshWhitelist_successUpdatesCache() = runTest {
        val cache = InMemoryAppWhitelistCache()
        val repository = DefaultAppWhitelistRepository(
            remoteDataSource = FakeAppWhitelistRemoteDataSource(
                apps = listOf(WhitelistedApp("it.fineco.mobile", "Fineco"))
            ),
            cache = cache,
            clock = FixedClock(nowMillis = 10_000L)
        )

        val result = repository.refreshWhitelist()

        assertTrue(result.isSuccess)
        assertEquals(setOf("it.fineco.mobile"), repository.getWhitelistedPackages())
        assertEquals(10_000L, cache.readSnapshot().lastSuccessfulSyncMillis)
    }

    @Test
    fun refreshWhitelist_failureDoesNotClearExistingCache() = runTest {
        val cache = InMemoryAppWhitelistCache(
            initialSnapshot = AppWhitelistCacheSnapshot(
                apps = listOf(WhitelistedApp("com.unicredit", "UniCredit")),
                lastSuccessfulSyncMillis = 5_000L
            )
        )
        val repository = DefaultAppWhitelistRepository(
            remoteDataSource = FakeAppWhitelistRemoteDataSource(
                failure = AppWhitelistSyncException.NonJsonContent("text/html")
            ),
            cache = cache,
            clock = FixedClock(nowMillis = 10_000L)
        )

        val result = repository.refreshWhitelist()

        assertTrue(result.isFailure)
        assertEquals(setOf("com.unicredit"), repository.getWhitelistedPackages())
        assertEquals(5_000L, cache.readSnapshot().lastSuccessfulSyncMillis)
    }


    @Test
    fun refreshWhitelist_remoteFailureWithEmptyCacheStoresBundledFallback() = runTest {
        val cache = InMemoryAppWhitelistCache()
        val repository = DefaultAppWhitelistRepository(
            remoteDataSource = FakeAppWhitelistRemoteDataSource(
                failure = AppWhitelistSyncException.RequestFailed(IllegalStateException("offline"))
            ),
            cache = cache,
            fallbackDataSource = FakeAppWhitelistFallbackDataSource(
                apps = listOf(WhitelistedApp("it.fineco.mobile", "Fineco"))
            ),
            clock = FixedClock(nowMillis = 10_000L)
        )

        val result = repository.refreshWhitelist()

        assertTrue(result.isSuccess)
        assertEquals(setOf("it.fineco.mobile"), repository.getWhitelistedPackages())
        assertEquals(10_000L, cache.readSnapshot().lastSuccessfulSyncMillis)
    }

    @Test
    fun refreshWhitelist_remoteFailureDoesNotUseBundledFallbackWhenCacheAlreadyExists() = runTest {
        val cache = InMemoryAppWhitelistCache(
            initialSnapshot = AppWhitelistCacheSnapshot(
                apps = listOf(WhitelistedApp("com.unicredit", "UniCredit")),
                lastSuccessfulSyncMillis = 5_000L
            )
        )
        val repository = DefaultAppWhitelistRepository(
            remoteDataSource = FakeAppWhitelistRemoteDataSource(
                failure = AppWhitelistSyncException.RequestFailed(IllegalStateException("offline"))
            ),
            cache = cache,
            fallbackDataSource = FakeAppWhitelistFallbackDataSource(
                apps = listOf(WhitelistedApp("it.fineco.mobile", "Fineco"))
            ),
            clock = FixedClock(nowMillis = 10_000L)
        )

        val result = repository.refreshWhitelist()

        assertTrue(result.isFailure)
        assertEquals(setOf("com.unicredit"), repository.getWhitelistedPackages())
        assertEquals(5_000L, cache.readSnapshot().lastSuccessfulSyncMillis)
    }

    @Test
    fun refreshWhitelist_remoteAndBundledFallbackFailureLeavesEmptyCache() = runTest {
        val cache = InMemoryAppWhitelistCache()
        val repository = DefaultAppWhitelistRepository(
            remoteDataSource = FakeAppWhitelistRemoteDataSource(
                failure = AppWhitelistSyncException.RequestFailed(IllegalStateException("offline"))
            ),
            cache = cache,
            fallbackDataSource = FakeAppWhitelistFallbackDataSource(
                failure = AppWhitelistSyncException.BundledFallbackUnavailable(IllegalStateException("missing asset"))
            ),
            clock = FixedClock(nowMillis = 10_000L)
        )

        val result = repository.refreshWhitelist()

        assertTrue(result.isFailure)
        assertEquals(emptySet(), repository.getWhitelistedPackages())
        assertEquals(null, cache.readSnapshot().lastSuccessfulSyncMillis)
    }

    @Test
    fun isCacheEmptyOrStale_returnsTrueWhenCacheIsEmpty() = runTest {
        val repository = DefaultAppWhitelistRepository(
            remoteDataSource = FakeAppWhitelistRemoteDataSource(),
            cache = InMemoryAppWhitelistCache(),
            clock = FixedClock(nowMillis = 10_000L)
        )

        assertTrue(repository.isCacheEmptyOrStale())
    }

    @Test
    fun isCacheEmptyOrStale_returnsTrueWhenLastSyncIsTooOld() = runTest {
        val repository = DefaultAppWhitelistRepository(
            remoteDataSource = FakeAppWhitelistRemoteDataSource(),
            cache = InMemoryAppWhitelistCache(
                AppWhitelistCacheSnapshot(
                    apps = listOf(WhitelistedApp("it.fineco.mobile", "Fineco")),
                    lastSuccessfulSyncMillis = 1_000L
                )
            ),
            clock = FixedClock(nowMillis = 5_001L),
            staleAfterMillis = 4_000L
        )

        assertTrue(repository.isCacheEmptyOrStale())
    }

    @Test
    fun observeWhitelistState_marksFreshCacheAsNotStale() = runTest {
        val repository = DefaultAppWhitelistRepository(
            remoteDataSource = FakeAppWhitelistRemoteDataSource(),
            cache = InMemoryAppWhitelistCache(
                AppWhitelistCacheSnapshot(
                    apps = listOf(WhitelistedApp("it.fineco.mobile", "Fineco")),
                    lastSuccessfulSyncMillis = 4_000L
                )
            ),
            clock = FixedClock(nowMillis = 5_000L),
            staleAfterMillis = 4_000L
        )

        val state = repository.observeWhitelistState().first()

        assertFalse(state.isStale)
        assertEquals(listOf(WhitelistedApp("it.fineco.mobile", "Fineco")), state.apps)
        assertEquals(4_000L, state.lastSuccessfulSyncMillis)
    }
}

private class FakeAppWhitelistRemoteDataSource(
    private val apps: List<WhitelistedApp> = emptyList(),
    private val failure: Throwable? = null
) : AppWhitelistRemoteDataSource {
    override suspend fun fetchWhitelist(): List<WhitelistedApp> {
        failure?.let { throw it }
        return apps
    }
}

private class FakeAppWhitelistFallbackDataSource(
    private val apps: List<WhitelistedApp> = emptyList(),
    private val failure: Throwable? = null
) : AppWhitelistFallbackDataSource {
    override suspend fun fetchFallbackWhitelist(): List<WhitelistedApp> {
        failure?.let { throw it }
        return apps
    }
}

private class InMemoryAppWhitelistCache(
    initialSnapshot: AppWhitelistCacheSnapshot = AppWhitelistCacheSnapshot(
        apps = emptyList(),
        lastSuccessfulSyncMillis = null
    )
) : AppWhitelistCache {
    private val state = MutableStateFlow(initialSnapshot)

    override suspend fun readSnapshot(): AppWhitelistCacheSnapshot = state.value

    override suspend fun replaceWhitelist(
        apps: List<WhitelistedApp>,
        lastSuccessfulSyncMillis: Long
    ) {
        state.value = AppWhitelistCacheSnapshot(apps, lastSuccessfulSyncMillis)
    }

    override fun observeSnapshot(): Flow<AppWhitelistCacheSnapshot> = state
}

private class FixedClock(
    private val nowMillis: Long
) : AppWhitelistClock {
    override fun nowMillis(): Long = nowMillis
}
