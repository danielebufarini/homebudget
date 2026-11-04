package it.danielebufarini.spesify.ui.screens

import it.danielebufarini.spesify.data.DEFAULT_TRANSACTION_SEARCH_PAGE_SIZE
import it.danielebufarini.spesify.data.ExpenseRepository
import it.danielebufarini.spesify.data.TransactionPageCursor
import it.danielebufarini.spesify.data.TransactionSearchPage
import it.danielebufarini.spesify.database.Category
import it.danielebufarini.spesify.database.Expense
import it.danielebufarini.spesify.database.Income
import it.danielebufarini.spesify.di.iosKoin
import kotlinx.coroutines.flow.first

internal interface IosTransactionSearchDataSource {
    suspend fun seedCategories()
    suspend fun categories(): List<Category>
    suspend fun expenses(query: String, cursor: TransactionPageCursor?): TransactionSearchPage<Expense>
    suspend fun incomes(query: String, cursor: TransactionPageCursor?): TransactionSearchPage<Income>
    suspend fun deleteExpense(id: String)
    suspend fun deleteIncome(id: String)
    suspend fun deleteExpenseSeries(id: String)
    suspend fun deleteIncomeSeries(id: String)
}

internal class RepositoryIosTransactionSearchDataSource : IosTransactionSearchDataSource {
    private fun repository(): ExpenseRepository = iosKoin().get()
    override suspend fun seedCategories() = repository().seedStarterCategoriesIfEmpty()
    override suspend fun categories() = repository().getAllCategories().first()
    override suspend fun expenses(query: String, cursor: TransactionPageCursor?) =
        repository().searchExpenseCandidatePage(query, DEFAULT_TRANSACTION_SEARCH_PAGE_SIZE, cursor).first()
    override suspend fun incomes(query: String, cursor: TransactionPageCursor?) =
        repository().searchIncomeCandidatePage(query, DEFAULT_TRANSACTION_SEARCH_PAGE_SIZE, cursor).first()
    override suspend fun deleteExpense(id: String) = repository().deleteExpense(id)
    override suspend fun deleteIncome(id: String) = repository().deleteIncome(id)
    override suspend fun deleteExpenseSeries(id: String) = repository().deleteRecurringExpenseSeries(id)
    override suspend fun deleteIncomeSeries(id: String) = repository().deleteRecurringIncomeSeries(id)
}
