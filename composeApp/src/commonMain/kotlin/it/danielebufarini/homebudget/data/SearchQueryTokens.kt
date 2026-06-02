package it.danielebufarini.homebudget.data

internal const val DEFAULT_TRANSACTION_SEARCH_PAGE_SIZE = 200

internal fun ftsSearchQuery(query: String): String? {
    val tokens = searchTokenRegex
        .findAll(query.lowercase())
        .map(MatchResult::value)
        .distinct()
        .take(8)
        .toList()

    return tokens
        .takeIf(List<String>::isNotEmpty)
        ?.joinToString(" AND ") { token -> "$token*" }
}

private val searchTokenRegex = Regex("[\\p{L}\\p{N}]+")
