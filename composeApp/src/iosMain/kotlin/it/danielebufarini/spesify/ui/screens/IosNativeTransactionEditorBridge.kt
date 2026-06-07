package it.danielebufarini.spesify.ui.screens

import it.danielebufarini.spesify.data.ExpenseRepository
import it.danielebufarini.spesify.data.IdGenerator
import it.danielebufarini.spesify.data.PendingIncome
import it.danielebufarini.spesify.data.buildPendingExpenses
import it.danielebufarini.spesify.data.buildRecurringMonthlyExpenses
import it.danielebufarini.spesify.data.buildRecurringMonthlyIncomes
import it.danielebufarini.spesify.data.evaluateAmountExpressionInput
import it.danielebufarini.spesify.data.formatAmountInput
import it.danielebufarini.spesify.di.initKoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatformTools

class IosNativeTransactionEditorResult(
    val isSuccess: Boolean,
    val errorKey: String? = null
)

class IosNativeIncomeEditorSnapshot(
    val id: String,
    val amountInput: String,
    val dateMillis: Long,
    val categoryId: String?,
    val descriptionText: String,
    val isRecurringMonthly: Boolean,
    val recurringSeriesId: String?
)

class IosNativeTransactionEditorController {
    private val repository: ExpenseRepository by lazy {
        ensureIosNativeTransactionEditorKoinStarted()
        KoinPlatformTools.defaultContext().get().get()
    }

    suspend fun loadIncome(id: String): IosNativeIncomeEditorSnapshot? {
        val income = withContext(Dispatchers.Default) {
            repository.getIncomeById(id)
        }
        return income?.let {
            IosNativeIncomeEditorSnapshot(
                id = it.id,
                amountInput = formatAmountInput(it.amount),
                dateMillis = it.date,
                categoryId = it.categoryId,
                descriptionText = it.description.orEmpty(),
                isRecurringMonthly = it.recurringSeriesId != null,
                recurringSeriesId = it.recurringSeriesId
            )
        }
    }

    suspend fun saveExpense(
        amountInput: String,
        dateMillis: Long,
        categoryId: String,
        description: String,
        isShared: Boolean,
        isRecurringMonthly: Boolean,
        installmentCount: Int
    ): IosNativeTransactionEditorResult {
        val parsedAmount = evaluateAmountExpressionInput(amountInput)
        if (parsedAmount == null || parsedAmount <= 0L) {
            return IosNativeTransactionEditorResult(false, "Enter a valid amount")
        }
        if (categoryId.isBlank()) {
            return IosNativeTransactionEditorResult(false, "Select Category")
        }
        if (dateMillis <= 0L) {
            return IosNativeTransactionEditorResult(false, "Select a date")
        }

        val result = withContext(Dispatchers.Default) {
            runCatching {
                val expenses = if (isRecurringMonthly) {
                    buildRecurringMonthlyExpenses(
                        amount = parsedAmount,
                        firstDate = dateMillis,
                        categoryId = categoryId,
                        description = description,
                        isShared = isShared,
                        recurringSeriesId = buildIosRecurringExpenseSeriesId(),
                        idProvider = ::buildIosExpenseId
                    )
                } else {
                    buildPendingExpenses(
                        amount = parsedAmount,
                        firstDate = dateMillis,
                        installments = installmentCount,
                        categoryId = categoryId,
                        description = description,
                        isShared = isShared,
                        idProvider = ::buildIosExpenseId
                    )
                }
                repository.insertExpenses(expenses)
            }
        }

        return if (result.isSuccess) {
            IosNativeTransactionEditorResult(true)
        } else {
            IosNativeTransactionEditorResult(false, "Unable to save expense.")
        }
    }

    suspend fun saveIncome(
        amountInput: String,
        dateMillis: Long,
        categoryId: String?,
        description: String,
        isRecurringMonthly: Boolean
    ): IosNativeTransactionEditorResult {
        val parsedAmount = evaluateAmountExpressionInput(amountInput)
        if (parsedAmount == null || parsedAmount <= 0L) {
            return IosNativeTransactionEditorResult(false, "Enter a valid amount")
        }
        if (dateMillis <= 0L) {
            return IosNativeTransactionEditorResult(false, "Select a date")
        }

        val normalizedCategoryId = categoryId?.takeIf { it.isNotBlank() }
        val result = withContext(Dispatchers.Default) {
            runCatching {
                val incomes = if (isRecurringMonthly) {
                    buildRecurringMonthlyIncomes(
                        amount = parsedAmount,
                        firstDate = dateMillis,
                        description = description,
                        categoryId = normalizedCategoryId,
                        recurringSeriesId = buildIosRecurringIncomeSeriesId(),
                        idProvider = ::buildIosIncomeId
                    )
                } else {
                    listOf(
                        PendingIncome(
                            id = buildIosIncomeId(),
                            amount = parsedAmount,
                            date = dateMillis,
                            description = description.ifBlank { null },
                            categoryId = normalizedCategoryId,
                            recurringSeriesId = null
                        )
                    )
                }
                repository.insertIncomes(incomes)
            }
        }

        return if (result.isSuccess) {
            IosNativeTransactionEditorResult(true)
        } else {
            IosNativeTransactionEditorResult(false, "Unable to save income.")
        }
    }

    suspend fun saveExistingIncome(
        incomeId: String,
        amountInput: String,
        dateMillis: Long,
        categoryId: String?,
        description: String,
        isRecurringMonthly: Boolean,
        updateWholeSeries: Boolean
    ): IosNativeTransactionEditorResult {
        val parsedAmount = evaluateAmountExpressionInput(amountInput)
        if (parsedAmount == null || parsedAmount <= 0L) {
            return IosNativeTransactionEditorResult(false, "Enter a valid amount")
        }
        if (dateMillis <= 0L) {
            return IosNativeTransactionEditorResult(false, "Select a date")
        }

        val normalizedCategoryId = categoryId?.takeIf { it.isNotBlank() }
        val normalizedDescription = description.ifBlank { null }
        val result = withContext(Dispatchers.Default) {
            runCatching {
                val existingIncome = repository.getIncomeById(incomeId)
                    ?: error("income-not-found")
                val recurringSeriesId = existingIncome.recurringSeriesId

                when {
                    recurringSeriesId != null && updateWholeSeries -> {
                        repository.updateRecurringIncomeSeries(
                            anchorIncomeId = existingIncome.id,
                            seriesId = recurringSeriesId,
                            amount = parsedAmount,
                            date = dateMillis,
                            description = normalizedDescription,
                            categoryId = normalizedCategoryId
                        )
                    }

                    recurringSeriesId != null -> {
                        repository.insertIncomes(
                            listOf(
                                PendingIncome(
                                    id = existingIncome.id,
                                    amount = parsedAmount,
                                    date = dateMillis,
                                    description = normalizedDescription,
                                    categoryId = normalizedCategoryId,
                                    recurringSeriesId = recurringSeriesId
                                )
                            )
                        )
                    }

                    isRecurringMonthly -> {
                        val incomes = buildRecurringMonthlyIncomes(
                            amount = parsedAmount,
                            firstDate = dateMillis,
                            description = normalizedDescription,
                            categoryId = normalizedCategoryId,
                            recurringSeriesId = buildIosRecurringIncomeSeriesId(),
                            idProvider = ::buildIosIncomeId
                        ).mapIndexed { index, income ->
                            if (index == 0) income.copy(id = existingIncome.id) else income
                        }
                        repository.insertIncomes(incomes)
                    }

                    else -> {
                        repository.insertIncomes(
                            listOf(
                                PendingIncome(
                                    id = existingIncome.id,
                                    amount = parsedAmount,
                                    date = dateMillis,
                                    description = normalizedDescription,
                                    categoryId = normalizedCategoryId,
                                    recurringSeriesId = null
                                )
                            )
                        )
                    }
                }
            }
        }

        return if (result.isSuccess) {
            IosNativeTransactionEditorResult(true)
        } else {
            IosNativeTransactionEditorResult(false, "Unable to save income.")
        }
    }

    suspend fun deleteIncome(
        incomeId: String,
        deleteWholeSeries: Boolean
    ): IosNativeTransactionEditorResult {
        val result = withContext(Dispatchers.Default) {
            runCatching {
                val existingIncome = repository.getIncomeById(incomeId)
                    ?: error("income-not-found")
                val recurringSeriesId = existingIncome.recurringSeriesId
                if (deleteWholeSeries && recurringSeriesId != null) {
                    repository.deleteRecurringIncomeSeries(recurringSeriesId)
                } else {
                    repository.deleteIncome(incomeId)
                }
            }
        }

        return if (result.isSuccess) {
            IosNativeTransactionEditorResult(true)
        } else {
            IosNativeTransactionEditorResult(false, "Unable to delete income.")
        }
    }

}

private fun ensureIosNativeTransactionEditorKoinStarted() {
    if (KoinPlatformTools.defaultContext().getOrNull() == null) {
        initKoin()
    }
}

private fun buildIosExpenseId(): String = IdGenerator.newId("expense")

private fun buildIosIncomeId(): String = IdGenerator.newId("income")

private fun buildIosRecurringExpenseSeriesId(): String = IdGenerator.newId("recurring-expense")

private fun buildIosRecurringIncomeSeriesId(): String = IdGenerator.newId("recurring-income")
