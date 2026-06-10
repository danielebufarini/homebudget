package it.danielebufarini.spesify.data.notifications

sealed class AppWhitelistSyncException(
    message: String,
    cause: Throwable? = null,
    val isTransient: Boolean
) : Exception(message, cause) {

    class RequestFailed(cause: Throwable) : AppWhitelistSyncException(
        message = "Unable to fetch the app whitelist.",
        cause = cause,
        isTransient = true
    )

    class HttpStatus(val statusCode: Int) : AppWhitelistSyncException(
        message = "App whitelist request failed with HTTP $statusCode.",
        isTransient = statusCode == 408 || statusCode == 429 || statusCode in 500..599
    )

    class NonJsonContent(val contentType: String?) : AppWhitelistSyncException(
        message = "App whitelist response is not JSON: ${contentType ?: "missing content type"}.",
        isTransient = false
    )

    class MalformedJson(cause: Throwable) : AppWhitelistSyncException(
        message = "App whitelist response contains malformed JSON.",
        cause = cause,
        isTransient = false
    )

    class InvalidEntry(reason: String) : AppWhitelistSyncException(
        message = "App whitelist contains an invalid entry: $reason.",
        isTransient = false
    )

    class BundledFallbackUnavailable(cause: Throwable) : AppWhitelistSyncException(
        message = "Bundled app whitelist fallback could not be read.",
        cause = cause,
        isTransient = false
    )
}

fun Throwable.isTransientAppWhitelistFailure(): Boolean {
    return this !is AppWhitelistSyncException || isTransient
}
