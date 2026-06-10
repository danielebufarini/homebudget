package it.danielebufarini.spesify.data.notifications

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration

internal class DefaultAppWhitelistRepository(
    private val remoteDataSource: AppWhitelistRemoteDataSource,
    private val cache: AppWhitelistCache,
    private val clock: AppWhitelistClock = SystemAppWhitelistClock,
    private val staleAfterMillis: Long = Duration.ofHours(24).toMillis()
) : AppWhitelistRepository {

    override suspend fun refreshWhitelist(): Result<Unit> {
        return runCatching {
            val apps = remoteDataSource.fetchWhitelist()
            cache.replaceWhitelist(
                apps = apps,
                lastSuccessfulSyncMillis = clock.nowMillis()
            )
        }
    }

    override suspend fun getWhitelistedPackages(): Set<String> {
        return cache.readSnapshot().apps.mapTo(mutableSetOf()) { it.packageName }
    }

    override suspend fun isCacheEmptyOrStale(): Boolean {
        return cache.readSnapshot().isEmptyOrStale()
    }

    override fun observeWhitelistState(): Flow<AppWhitelistState> {
        return cache.observeSnapshot().map { snapshot -> snapshot.toState() }
    }

    private fun AppWhitelistCacheSnapshot.toState(): AppWhitelistState {
        return AppWhitelistState(
            apps = apps,
            lastSuccessfulSyncMillis = lastSuccessfulSyncMillis,
            isStale = isEmptyOrStale()
        )
    }

    private fun AppWhitelistCacheSnapshot.isEmptyOrStale(): Boolean {
        val lastSync = lastSuccessfulSyncMillis ?: return true
        if (apps.isEmpty()) return true
        return clock.nowMillis() - lastSync >= staleAfterMillis
    }
}
