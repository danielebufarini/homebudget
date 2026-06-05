package it.danielebufarini.homebudget.ui.screens

import it.danielebufarini.homebudget.data.DEFAULT_TRANSACTION_SEARCH_PAGE_SIZE
import it.danielebufarini.homebudget.data.ExpenseReadRepository
import it.danielebufarini.homebudget.data.IncomeReadRepository
import it.danielebufarini.homebudget.data.TransactionSearchPage
import it.danielebufarini.homebudget.database.Expense
import it.danielebufarini.homebudget.database.Income
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

@OptIn(ExperimentalCoroutinesApi::class)
internal fun ExpenseReadRepository.searchExpenseCandidatePages(
    query: String,
    pageCount: Int,
    pageSize: Int = DEFAULT_TRANSACTION_SEARCH_PAGE_SIZE
): Flow<TransactionSearchResults<Expense>> {
    val safePageCount = pageCount.coerceAtLeast(1)
    val safePageSize = pageSize.coerceAtLeast(1)
    return searchExpenseCandidatePage(
        query = query,
        limit = safePageSize,
        cursor = null
    ).flatMapLatest { firstPage ->
        flow {
            emit(
                loadExpenseSearchPages(
                    query = query,
                    pageSize = safePageSize,
                    pageCount = safePageCount,
                    firstPage = firstPage
                )
            )
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
internal fun IncomeReadRepository.searchIncomeCandidatePages(
    query: String,
    pageCount: Int,
    pageSize: Int = DEFAULT_TRANSACTION_SEARCH_PAGE_SIZE
): Flow<TransactionSearchResults<Income>> {
    val safePageCount = pageCount.coerceAtLeast(1)
    val safePageSize = pageSize.coerceAtLeast(1)
    return searchIncomeCandidatePage(
        query = query,
        limit = safePageSize,
        cursor = null
    ).flatMapLatest { firstPage ->
        flow {
            emit(
                loadIncomeSearchPages(
                    query = query,
                    pageSize = safePageSize,
                    pageCount = safePageCount,
                    firstPage = firstPage
                )
            )
        }
    }
}

internal data class TransactionSearchResults<T>(
    val items: List<T>,
    val canLoadMore: Boolean
)

internal data class TransactionSearchPaging(
    val searchMode: Boolean,
    val pageCount: Int,
    val loadMoreSearchResults: () -> Unit
)

internal fun transactionSearchPaging(
    searchQuery: String,
    externalSearchPageCount: Int?,
    localSearchPageCount: Int,
    onLocalSearchPageCountChange: (Int) -> Unit,
    onLoadMoreSearchResults: (() -> Unit)?,
    pageSize: Int = DEFAULT_TRANSACTION_SEARCH_PAGE_SIZE
) = searchQuery.isNotBlank().let { searchMode ->
    val pageCount = if (searchMode) externalSearchPageCount ?: localSearchPageCount else 1

    TransactionSearchPaging(
        searchMode = searchMode,
        pageCount = pageCount,
        loadMoreSearchResults = onLoadMoreSearchResults ?: {
            onLocalSearchPageCountChange(localSearchPageCount + 1)
        }
    )
}

private suspend fun ExpenseReadRepository.loadExpenseSearchPages(
    query: String,
    pageSize: Int,
    pageCount: Int,
    firstPage: TransactionSearchPage<Expense>
): TransactionSearchResults<Expense> {
    val loaded = firstPage.items.toMutableList()
    var cursor = firstPage.nextCursor
    var canLoadMore = firstPage.canLoadMore

    repeat(pageCount - 1) {
        if (!canLoadMore || cursor == null) {
            return@repeat
        }
        val nextPage = searchExpenseCandidatePage(
            query = query,
            limit = pageSize,
            cursor = cursor
        ).first()
        loaded += nextPage.items
        cursor = nextPage.nextCursor
        canLoadMore = nextPage.canLoadMore
    }

    return TransactionSearchResults(
        items = loaded.distinctBy(Expense::id),
        canLoadMore = canLoadMore
    )
}

private suspend fun IncomeReadRepository.loadIncomeSearchPages(
    query: String,
    pageSize: Int,
    pageCount: Int,
    firstPage: TransactionSearchPage<Income>
): TransactionSearchResults<Income> {
    val loaded = firstPage.items.toMutableList()
    var cursor = firstPage.nextCursor
    var canLoadMore = firstPage.canLoadMore

    repeat(pageCount - 1) {
        if (!canLoadMore || cursor == null) {
            return@repeat
        }
        val nextPage = searchIncomeCandidatePage(
            query = query,
            limit = pageSize,
            cursor = cursor
        ).first()
        loaded += nextPage.items
        cursor = nextPage.nextCursor
        canLoadMore = nextPage.canLoadMore
    }

    return TransactionSearchResults(
        items = loaded.distinctBy(Income::id),
        canLoadMore = canLoadMore
    )
}
