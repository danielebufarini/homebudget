package it.danielebufarini.spesify.data.notifications

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json

private const val DEFAULT_APP_WHITELIST_URL =
    "https://codeberg.org/danielebufarini/homebudget/raw/branch/main/android_banks_packages_list.json"

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

        Log.d(
            TAG,
            "Whitelist HTTP response status=${response.statusCode} contentType=${response.contentType ?: "missing"} bodyLength=${response.body.length}"
        )

        if (!response.statusCode.isSuccess()) {
            throw AppWhitelistSyncException.HttpStatus(response.statusCode)
        }

        if (response.isClearlyHtml()) {
            Log.e(TAG, "Whitelist response rejected because it looks like HTML contentType=${response.contentType ?: "missing"}")
            throw AppWhitelistSyncException.NonJsonContent(response.contentType)
        }

        if (response.contentType.isJsonContentType() || response.body.looksLikeJsonPayload()) {
            return parseWhitelistedAppsJson(response.body, json)
        }

        Log.e(TAG, "Whitelist response rejected because it is not JSON contentType=${response.contentType ?: "missing"}")
        throw AppWhitelistSyncException.NonJsonContent(response.contentType)
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

    private fun String?.isHtmlContentType(): Boolean {
        val normalized = this
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
            ?: return false
        return normalized == "text/html" || normalized == "application/xhtml+xml"
    }

    private fun AppWhitelistHttpResponse.isClearlyHtml(): Boolean {
        if (contentType.isHtmlContentType()) return true
        return body.trimStart().startsWith("<")
    }

    private fun String.looksLikeJsonPayload(): Boolean {
        return trimStart().firstOrNull() in setOf('[', '{')
    }

    private companion object {
        const val TAG = "SpesifyNotifDetect"
    }
}
