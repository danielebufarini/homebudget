package it.danielebufarini.spesify.data.notifications

import kotlinx.coroutines.flow.Flow

data class WhitelistedApp(
    val packageName: String,
    val bankName: String?
)

data class AppWhitelistState(
    val apps: List<WhitelistedApp>,
    val lastSuccessfulSyncMillis: Long?,
    val isStale: Boolean
)

interface AppWhitelistRepository {
    suspend fun refreshWhitelist(): Result<Unit>
    suspend fun getWhitelistedPackages(): Set<String>
    suspend fun isCacheEmptyOrStale(): Boolean
    fun observeWhitelistState(): Flow<AppWhitelistState>
}

internal data class AppWhitelistCacheSnapshot(
    val apps: List<WhitelistedApp>,
    val lastSuccessfulSyncMillis: Long?
)

internal interface AppWhitelistCache {
    suspend fun readSnapshot(): AppWhitelistCacheSnapshot
    suspend fun replaceWhitelist(apps: List<WhitelistedApp>, lastSuccessfulSyncMillis: Long)
    fun observeSnapshot(): Flow<AppWhitelistCacheSnapshot>
}

internal interface AppWhitelistRemoteDataSource {
    suspend fun fetchWhitelist(): List<WhitelistedApp>
}

internal fun interface AppWhitelistClock {
    fun nowMillis(): Long
}

internal object SystemAppWhitelistClock : AppWhitelistClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
