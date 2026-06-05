package it.danielebufarini.homebudget.data

internal const val DEFAULT_TRANSACTION_SEARCH_PAGE_SIZE = 200

data class TransactionPageCursor(
    val date: Long,
    val id: String
)

data class TransactionSearchPage<T>(
    val items: List<T>,
    val nextCursor: TransactionPageCursor?,
    val canLoadMore: Boolean
)

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

internal fun <T> List<T>.toTransactionSearchPage(
    limit: Int,
    itemDate: (T) -> Long,
    itemId: (T) -> String
): TransactionSearchPage<T> {
    val safeLimit = limit.coerceAtLeast(1)
    val pageItems = take(safeLimit)
    val nextCursor = pageItems.lastOrNull()?.let { item ->
        TransactionPageCursor(
            date = itemDate(item),
            id = itemId(item)
        )
    }
    return TransactionSearchPage(
        items = pageItems,
        nextCursor = nextCursor,
        canLoadMore = size > safeLimit
    )
}
