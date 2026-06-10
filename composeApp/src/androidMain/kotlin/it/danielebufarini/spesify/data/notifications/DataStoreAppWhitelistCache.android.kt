package it.danielebufarini.spesify.data.notifications

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val APP_WHITELIST_DATA_STORE_NAME = "app_whitelist_cache"

private val Context.appWhitelistDataStore by preferencesDataStore(
    name = APP_WHITELIST_DATA_STORE_NAME
)

internal class DataStoreAppWhitelistCache(
    private val context: Context,
    private val json: Json = appWhitelistJson
) : AppWhitelistCache {

    override suspend fun readSnapshot(): AppWhitelistCacheSnapshot {
        return observeSnapshot().first()
    }

    override suspend fun replaceWhitelist(
        apps: List<WhitelistedApp>,
        lastSuccessfulSyncMillis: Long
    ) {
        val storedApps = apps.map { app ->
            StoredWhitelistedApp(
                packageName = app.packageName,
                bankName = app.bankName
            )
        }
        context.appWhitelistDataStore.edit { preferences ->
            preferences[WHITELIST_JSON] = json.encodeToString(storedApps)
            preferences[LAST_SUCCESSFUL_SYNC_MILLIS] = lastSuccessfulSyncMillis
        }
    }

    override fun observeSnapshot(): Flow<AppWhitelistCacheSnapshot> {
        return context.appWhitelistDataStore.data.map { preferences ->
            val apps = preferences[WHITELIST_JSON]
                ?.let(::decodeStoredApps)
                .orEmpty()
            AppWhitelistCacheSnapshot(
                apps = apps,
                lastSuccessfulSyncMillis = preferences[LAST_SUCCESSFUL_SYNC_MILLIS]
            )
        }
    }

    private fun decodeStoredApps(rawJson: String): List<WhitelistedApp> {
        return runCatching {
            json.decodeFromString<List<StoredWhitelistedApp>>(rawJson).map { app ->
                WhitelistedApp(
                    packageName = app.packageName,
                    bankName = app.bankName
                )
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        val WHITELIST_JSON = stringPreferencesKey("whitelist_json")
        val LAST_SUCCESSFUL_SYNC_MILLIS = longPreferencesKey("last_successful_sync_millis")
    }
}

@Serializable
private data class StoredWhitelistedApp(
    val packageName: String,
    val bankName: String? = null
)
