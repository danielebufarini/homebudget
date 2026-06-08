package it.danielebufarini.spesify.data

import it.danielebufarini.spesify.database.RECURRING_TRANSACTION_FREQUENCY_MONTHLY
import it.danielebufarini.spesify.database.RECURRING_TRANSACTION_KIND_EXPENSE
import it.danielebufarini.spesify.database.RECURRING_TRANSACTION_KIND_INCOME
import it.danielebufarini.spesify.database.RecurringTransactionRule
import it.danielebufarini.spesify.database.toStoredYearMonth
import kotlin.time.Clock

const val RECURRING_MATERIALIZATION_FORWARD_MONTHS = 12

internal fun List<PendingExpense>.toRecurringExpenseRules(): List<RecurringTransactionRule> {
    return groupedExpenseBySeriesId()
        .filterValues { it.size > 1 }
        .map { (_, seriesExpenses) ->
        val firstExpense = seriesExpenses.minBy { it.date }
        RecurringTransactionRule(
            id = firstExpense.recurringSeriesId.orEmpty(),
            kind = RECURRING_TRANSACTION_KIND_EXPENSE,
            amount = firstExpense.amount,
            startDate = firstExpense.date,
            frequency = RECURRING_TRANSACTION_FREQUENCY_MONTHLY,
            intervalMonths = 1,
            generatedThroughYearMonth = seriesExpenses.maxOf { it.date.toStoredYearMonth() },
            categoryId = firstExpense.categoryId,
            description = firstExpense.description,
            isShared = if (firstExpense.isShared) 1L else 0L
        )
    }
}

internal fun List<PendingIncome>.toRecurringIncomeRules(): List<RecurringTransactionRule> {
    return groupedIncomeBySeriesId()
        .filterValues { it.size > 1 }
        .map { (_, seriesIncomes) ->
        val firstIncome = seriesIncomes.minBy { it.date }
        RecurringTransactionRule(
            id = firstIncome.recurringSeriesId.orEmpty(),
            kind = RECURRING_TRANSACTION_KIND_INCOME,
            amount = firstIncome.amount,
            startDate = firstIncome.date,
            frequency = RECURRING_TRANSACTION_FREQUENCY_MONTHLY,
            intervalMonths = 1,
            generatedThroughYearMonth = seriesIncomes.maxOf { it.date.toStoredYearMonth() },
            categoryId = firstIncome.categoryId,
            description = firstIncome.description,
            isShared = 0L
        )
    }
}

internal fun recurringMaterializationTargetYearMonth(
    fromMillis: Long = Clock.System.now().toEpochMilliseconds()
): Int {
    return fromMillis.toMonthKey()
        .plusMonths(RECURRING_MATERIALIZATION_FORWARD_MONTHS)
        .toYearMonthKey()
}

private fun List<PendingExpense>.groupedExpenseBySeriesId(): Map<String, List<PendingExpense>> {
    return filter { !it.recurringSeriesId.isNullOrBlank() }
        .groupBy { it.recurringSeriesId.orEmpty() }
}

private fun List<PendingIncome>.groupedIncomeBySeriesId(): Map<String, List<PendingIncome>> {
    return filter { !it.recurringSeriesId.isNullOrBlank() }
        .groupBy { it.recurringSeriesId.orEmpty() }
}
