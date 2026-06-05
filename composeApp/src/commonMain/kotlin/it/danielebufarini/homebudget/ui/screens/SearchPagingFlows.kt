package it.danielebufarini.homebudget.ui.screens

import it.danielebufarini.homebudget.data.DEFAULT_TRANSACTION_SEARCH_PAGE_SIZE
import it.danielebufarini.homebudget.data.ExpenseReadRepository
import it.danielebufarini.homebudget.data.IncomeReadRepository
import it.danielebufarini.homebudget.database.Expense
import it.danielebufarini.homebudget.database.Income
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

internal fun ExpenseReadRepository.searchExpenseCandidatePages(
    query: String,
    pageCount: Int,
    pageSize: Int = DEFAULT_TRANSACTION_SEARCH_PAGE_SIZE
): Flow<List<Expense>> {
    val pages = searchPageOffsets(pageCount = pageCount, pageSize = pageSize).map { offset ->
        searchExpenseCandidates(
            query = query,
            limit = pageSize,
            offset = offset
        )
    }

    return combine(pages) { pageResults: Array<List<Expense>> ->
        pageResults
            .flatMap { page -> page.asIterable() }
            .distinctBy(Expense::id)
    }
}

internal fun IncomeReadRepository.searchIncomeCandidatePages(
    query: String,
    pageCount: Int,
    pageSize: Int = DEFAULT_TRANSACTION_SEARCH_PAGE_SIZE
): Flow<List<Income>> {
    val pages = searchPageOffsets(pageCount = pageCount, pageSize = pageSize).map { offset ->
        searchIncomeCandidates(
            query = query,
            limit = pageSize,
            offset = offset
        )
    }

    return combine(pages) { pageResults: Array<List<Income>> ->
        pageResults
            .flatMap { page -> page.asIterable() }
            .distinctBy(Income::id)
    }
}

internal data class TransactionSearchPaging(
    val searchMode: Boolean,
    val pageCount: Int,
    val loadedCandidateCount: Int,
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
        loadedCandidateCount = pageCount * pageSize,
        loadMoreSearchResults = onLoadMoreSearchResults ?: {
            onLocalSearchPageCountChange(localSearchPageCount + 1)
        }
    )
}


private fun searchPageOffsets(pageCount: Int, pageSize: Int): List<Int> {
    val safePageCount = pageCount.coerceAtLeast(1)
    val safePageSize = pageSize.coerceAtLeast(1)
    return List(safePageCount) { pageIndex -> pageIndex * safePageSize }
}
