package it.danielebufarini.spesify.data.notifications

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Serializable
private data class RemoteWhitelistedAppDto(
    val packageName: String,
    val bankName: String? = null
)

internal val appWhitelistJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

internal fun parseWhitelistedAppsJson(
    rawJson: String,
    json: Json = appWhitelistJson
): List<WhitelistedApp> {
    val decoded = try {
        json.decodeFromString<List<RemoteWhitelistedAppDto>>(rawJson)
    } catch (error: SerializationException) {
        throw AppWhitelistSyncException.MalformedJson(error)
    } catch (error: IllegalArgumentException) {
        throw AppWhitelistSyncException.MalformedJson(error)
    }

    return decoded
        .map { dto ->
            val packageName = dto.packageName.trim()
            if (packageName.isBlank()) {
                throw AppWhitelistSyncException.InvalidEntry("packageName is blank")
            }
            WhitelistedApp(
                packageName = packageName,
                bankName = dto.bankName?.trim()?.takeIf { it.isNotBlank() }
            )
        }
        .distinctBy { it.packageName }
        .sortedBy { it.packageName }
}
