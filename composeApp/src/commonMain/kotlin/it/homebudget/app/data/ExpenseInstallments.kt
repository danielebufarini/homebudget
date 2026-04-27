package it.homebudget.app.data

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.toBigInteger
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

data class PendingExpense(
    val id: String,
    val amount: BigInteger,
    val date: Long,
    val categoryId: String,
    val description: String?,
    val isShared: Boolean,
    val recurringSeriesId: String? = null
)

const val RECURRING_MONTHLY_OCCURRENCES = 240

fun buildPendingExpenses(
    amount: BigInteger,
    firstDate: Long,
    installments: Int,
    categoryId: String,
    description: String?,
    isShared: Boolean,
    idProvider: () -> String,
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): List<PendingExpense> {
    require(installments in 1..12) { "installments must be between 1 and 12" }

    val installmentAmounts = splitAmountIntoInstallments(amount, installments)

    return installmentAmounts.mapIndexed { index, installmentAmount ->
        PendingExpense(
            id = idProvider(),
            amount = installmentAmount,
            date = monthlyOccurrenceDate(firstDate, index, timeZone),
            categoryId = categoryId,
            description = description.ifBlankToNull(),
            isShared = isShared,
            recurringSeriesId = null
        )
    }
}

fun buildRecurringMonthlyExpenses(
    amount: BigInteger,
    firstDate: Long,
    categoryId: String,
    description: String?,
    isShared: Boolean,
    recurringSeriesId: String,
    idProvider: () -> String,
    occurrences: Int = RECURRING_MONTHLY_OCCURRENCES,
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): List<PendingExpense> {
    require(occurrences > 0) { "occurrences must be greater than 0" }

    return List(occurrences) { index ->
        PendingExpense(
            id = idProvider(),
            amount = amount,
            date = monthlyOccurrenceDate(firstDate, index, timeZone),
            categoryId = categoryId,
            description = description.ifBlankToNull(),
            isShared = isShared,
            recurringSeriesId = recurringSeriesId
        )
    }
}

fun splitAmountIntoInstallments(amount: BigInteger, installments: Int): List<BigInteger> {
    require(installments > 0) { "installments must be greater than 0" }

    val count = installments.toBigInteger()
    val baseAmount = amount / count
    val remainder = (amount % count).intValue()

    return List(installments) { index ->
        if (index < remainder) baseAmount + BigInteger.ONE else baseAmount
    }
}

private fun monthlyOccurrenceDate(
    firstDate: Long,
    monthOffset: Int,
    timeZone: TimeZone
): Long {
    val firstLocalDate = Instant.fromEpochMilliseconds(firstDate)
        .toLocalDateTime(timeZone)
        .date

    return firstLocalDate
        .plus(DatePeriod(months = monthOffset))
        .atStartOfDayIn(timeZone)
        .toEpochMilliseconds()
}

private fun String?.ifBlankToNull(): String? = this?.takeIf { it.isNotBlank() }
