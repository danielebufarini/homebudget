package it.danielebufarini.spesify.ui.screens

import it.danielebufarini.spesify.data.ExpenseRepository
import it.danielebufarini.spesify.data.PendingExpense
import it.danielebufarini.spesify.localization.unableToSaveExpenseMessage
import org.jetbrains.compose.resources.getString
import spesify.composeapp.generated.resources.Res
import spesify.composeapp.generated.resources.expense_not_found

internal suspend fun createIosVoiceExpense(
    repository: ExpenseRepository,
    amountInput: String,
    categoryId: String,
    description: String?,
    date: Long,
    isShared: Boolean
): Pair<Boolean, String?> {
    return runIosVoiceExpenseSave(
        amountInput = amountInput,
        categoryId = categoryId
    ) { amount ->
        repository.insertExpenses(
            listOf(
                PendingExpense(
                    id = buildIosVoiceExpenseId(),
                    amount = amount,
                    date = normalizeIosVoiceExpenseDate(date),
                    categoryId = categoryId,
                    description = description?.takeIf(String::isNotBlank),
                    isShared = isShared,
                    recurringSeriesId = null
                )
            )
        )
    }
}

internal suspend fun updateIosVoiceExpense(
    repository: ExpenseRepository,
    expenseId: String,
    amountInput: String,
    categoryId: String,
    description: String?,
    date: Long,
    isShared: Boolean
): Pair<Boolean, String?> {
    return runIosVoiceExpenseSave(
        amountInput = amountInput,
        categoryId = categoryId
    ) { amount ->
        val existingExpense = repository.getExpenseById(expenseId)
            ?: error(getString(Res.string.expense_not_found))
        repository.insertExpenses(
            listOf(
                PendingExpense(
                    id = existingExpense.id,
                    amount = amount,
                    date = normalizeIosVoiceExpenseDate(date),
                    categoryId = categoryId,
                    description = description?.takeIf(String::isNotBlank),
                    isShared = isShared,
                    recurringSeriesId = existingExpense.recurringSeriesId
                )
            )
        )
    }
}

private suspend fun runIosVoiceExpenseSave(
    amountInput: String,
    categoryId: String,
    block: suspend (Long) -> Unit
): Pair<Boolean, String?> {
    val (parsedAmount, error) = validateIosVoiceExpenseInput(
        amountInput = amountInput,
        categoryId = categoryId
    )
    if (error != null) return false to error
    val amount = parsedAmount ?: return false to getString(Res.string.expense_not_found)
    return runCatching { block(amount) }
        .fold(
            onSuccess = { true to null },
            onFailure = { false to (it.message ?: unableToSaveExpenseMessage()) }
        )
}
