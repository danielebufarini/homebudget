package it.danielebufarini.spesify.data.notifications

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private const val DEFAULT_BUNDLED_WHITELIST_ASSET = "android_banks_packages_list.json"

internal class AssetsAppWhitelistFallbackDataSource(
    private val context: Context,
    private val json: Json = appWhitelistJson,
    private val assetFileName: String = DEFAULT_BUNDLED_WHITELIST_ASSET
) : AppWhitelistFallbackDataSource {

    override suspend fun fetchFallbackWhitelist(): List<WhitelistedApp> {
        val rawJson = try {
            withContext(Dispatchers.IO) {
                context.assets.open(assetFileName).bufferedReader().use { reader ->
                    reader.readText()
                }
            }
        } catch (error: Exception) {
            Log.e(TAG, "Bundled whitelist fallback could not be read asset=$assetFileName", error)
            throw AppWhitelistSyncException.BundledFallbackUnavailable(error)
        }

        return parseWhitelistedAppsJson(rawJson, json)
    }

    private companion object {
        const val TAG = "SpesifyNotifDetect"
    }
}
