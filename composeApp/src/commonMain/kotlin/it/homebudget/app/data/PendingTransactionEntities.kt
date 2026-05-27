package it.homebudget.app.data

import it.homebudget.app.database.Expense
import it.homebudget.app.database.Income

internal fun PendingExpense.toEntity(): Expense {
    return Expense(
        id = id,
        amount = amount,
        date = date,
        categoryId = categoryId,
        description = description,
        isShared = if (isShared) 1L else 0L,
        recurringSeriesId = recurringSeriesId
    )
}

internal fun PendingIncome.toEntity(): Income {
    return Income(
        id = id,
        amount = amount,
        date = date,
        description = description,
        recurringSeriesId = recurringSeriesId,
        categoryId = categoryId
    )
}
