package it.homebudget.app.ui.screens

import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.database.Expense
import it.homebudget.app.database.Income
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

internal fun ExpenseRepository.searchExpenseCandidatePages(
    query: String,
    pageCount: Int,
    pageSize: Int
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

internal fun ExpenseRepository.searchIncomeCandidatePages(
    query: String,
    pageCount: Int,
    pageSize: Int
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

private fun searchPageOffsets(pageCount: Int, pageSize: Int): List<Int> {
    val safePageCount = pageCount.coerceAtLeast(1)
    val safePageSize = pageSize.coerceAtLeast(1)
    return List(safePageCount) { pageIndex -> pageIndex * safePageSize }
}
