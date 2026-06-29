package it.danielebufarini.spesify.data

import it.danielebufarini.spesify.database.Expense

data class RecurringExpenseOverview(
    val expenses: List<Expense>,
    val totalAmount: Long,
    val nextOccurrenceDate: Long?
)

fun buildRecurringExpenseOverview(expenses: List<Expense>): RecurringExpenseOverview {
    val recurringExpenses = expenses
        .filter { expense -> expense.recurringSeriesId != null }
        .sortedWith(compareBy<Expense> { it.date }.thenBy { it.id })

    return RecurringExpenseOverview(
        expenses = recurringExpenses,
        totalAmount = recurringExpenses.sumAmountOf(Expense::amount),
        nextOccurrenceDate = recurringExpenses.firstOrNull()?.date
    )
}
