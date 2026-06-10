package it.danielebufarini.spesify.data.notifications

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json

private const val DEFAULT_APP_WHITELIST_URL = "https://www.codeberg.org/danielebufarini/homebudget/android_banks_packages_list.json"

internal data class AppWhitelistHttpResponse(
    val statusCode: Int,
    val contentType: String?,
    val body: String
)

internal fun interface AppWhitelistHttpTransport {
    suspend fun get(url: String): AppWhitelistHttpResponse
}

internal class KtorAppWhitelistHttpTransport(
    private val client: HttpClient
) : AppWhitelistHttpTransport {
    override suspend fun get(url: String): AppWhitelistHttpResponse {
        val response = client.get(url)
        return AppWhitelistHttpResponse(
            statusCode = response.status.value,
            contentType = response.headers[HttpHeaders.ContentType],
            body = response.bodyAsText()
        )
    }
}

internal class KtorAppWhitelistRemoteDataSource(
    private val transport: AppWhitelistHttpTransport,
    private val json: Json = appWhitelistJson,
    private val url: String = DEFAULT_APP_WHITELIST_URL
) : AppWhitelistRemoteDataSource {

    override suspend fun fetchWhitelist(): List<WhitelistedApp> {
        val response = try {
            transport.get(url)
        } catch (error: AppWhitelistSyncException) {
            throw error
        } catch (error: Exception) {
            throw AppWhitelistSyncException.RequestFailed(error)
        }

        if (!response.statusCode.isSuccess()) {
            throw AppWhitelistSyncException.HttpStatus(response.statusCode)
        }

        if (!response.contentType.isJsonContentType()) {
            throw AppWhitelistSyncException.NonJsonContent(response.contentType)
        }

        return parseWhitelistedAppsJson(response.body, json)
    }

    private fun Int.isSuccess(): Boolean = this in 200..299

    private fun String?.isJsonContentType(): Boolean {
        val normalized = this
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
            ?: return false
        return normalized == "application/json" || normalized.endsWith("+json")
    }
}
