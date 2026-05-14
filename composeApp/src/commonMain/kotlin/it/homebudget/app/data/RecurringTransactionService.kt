package it.homebudget.app.data

class RecurringTransactionService(
    private val expenseEntryRepository: ExpenseEntryRepository,
    private val incomeRepository: IncomeRepository
) {
    suspend fun updateRecurringIncomeSeries(
        anchorIncomeId: String,
        seriesId: String,
        amount: Long,
        date: Long,
        description: String?,
        categoryId: String?
    ) {
        val seriesItems = incomeRepository.getRecurringIncomesBySeries(seriesId)
            .map { income ->
                ExistingRecurringIncomeItem(
                    id = income.id,
                    date = income.date
                )
            }

        incomeRepository.insertIncomes(
            buildUpdatedRecurringIncomeSeries(
                existingItems = seriesItems,
                anchorItemId = anchorIncomeId,
                anchorDate = date,
                amount = amount,
                description = description,
                categoryId = categoryId,
                recurringSeriesId = seriesId
            )
        )
    }

    suspend fun updateRecurringExpenseSeries(
        anchorExpenseId: String,
        seriesId: String,
        amount: Long,
        date: Long,
        categoryId: String,
        description: String?,
        isShared: Boolean
    ) {
        val seriesItems = expenseEntryRepository.getRecurringExpensesBySeries(seriesId)
            .map { expense ->
                ExistingRecurringExpenseItem(
                    id = expense.id,
                    date = expense.date
                )
            }

        expenseEntryRepository.insertExpenses(
            buildUpdatedRecurringExpenseSeries(
                existingItems = seriesItems,
                anchorItemId = anchorExpenseId,
                anchorDate = date,
                amount = amount,
                categoryId = categoryId,
                description = description,
                isShared = isShared,
                recurringSeriesId = seriesId
            )
        )
    }
}
