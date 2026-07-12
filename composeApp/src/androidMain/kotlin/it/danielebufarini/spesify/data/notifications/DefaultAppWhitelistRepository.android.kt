package it.danielebufarini.spesify.data.notifications

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration

internal class DefaultAppWhitelistRepository(
    private val remoteDataSource: AppWhitelistRemoteDataSource,
    private val cache: AppWhitelistCache,
    private val fallbackDataSource: AppWhitelistFallbackDataSource? = null,
    private val clock: AppWhitelistClock = SystemAppWhitelistClock,
    private val staleAfterMillis: Long = Duration.ofHours(24).toMillis()
) : AppWhitelistRepository {

    override suspend fun refreshWhitelist(): Result<Unit> {
        Log.d(TAG, "Whitelist refresh started")

        return try {
            val apps = remoteDataSource.fetchWhitelist()
            Log.d(
                TAG,
                "Whitelist refresh fetched count=${apps.size} finecoPresent=${apps.any { it.packageName == FINECO_TEST_PACKAGE }}"
            )
            storeWhitelist(apps)
            Log.d(TAG, "Whitelist refresh stored remote count=${apps.size}")
            Result.success(Unit)
        } catch (remoteError: Throwable) {
            Log.e(
                TAG,
                "Whitelist refresh failed type=${remoteError::class.java.simpleName} message=${remoteError.message}",
                remoteError
            )
            tryStoreBundledFallbackIfCacheIsEmpty(remoteError)
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

    private suspend fun tryStoreBundledFallbackIfCacheIsEmpty(remoteError: Throwable): Result<Unit> {
        val snapshot = cache.readSnapshot()
        if (snapshot.apps.isNotEmpty()) {
            Log.d(TAG, "Bundled whitelist fallback skipped because cache already contains ${snapshot.apps.size} packages")
            return Result.failure(remoteError)
        }

        val fallback = fallbackDataSource
        if (fallback == null) {
            Log.d(TAG, "Bundled whitelist fallback unavailable because no fallback data source is configured")
            return Result.failure(remoteError)
        }

        return try {
            Log.w(TAG, "Whitelist cache is empty; trying bundled fallback after remote sync failure")
            val fallbackApps = fallback.fetchFallbackWhitelist()
            storeWhitelist(fallbackApps)
            Log.w(
                TAG,
                "Bundled whitelist fallback stored count=${fallbackApps.size} finecoPresent=${fallbackApps.any { it.packageName == FINECO_TEST_PACKAGE }}"
            )
            Result.success(Unit)
        } catch (fallbackError: Throwable) {
            remoteError.addSuppressed(fallbackError)
            Log.e(
                TAG,
                "Bundled whitelist fallback failed type=${fallbackError::class.java.simpleName} message=${fallbackError.message}",
                fallbackError
            )
            Result.failure(remoteError)
        }
    }

    private suspend fun storeWhitelist(apps: List<WhitelistedApp>) {
        cache.replaceWhitelist(
            apps = apps,
            lastSuccessfulSyncMillis = clock.nowMillis()
        )
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

    private companion object {
        const val TAG = "SpesifyNotifDetect"
        const val FINECO_TEST_PACKAGE = "com.fineco.it"
    }
}
