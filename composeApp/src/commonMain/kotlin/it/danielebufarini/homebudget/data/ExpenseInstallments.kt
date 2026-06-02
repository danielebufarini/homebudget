package it.danielebufarini.homebudget.data
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

data class PendingExpense(
    val id: String,
    val amount: Long,
    val date: Long,
    val categoryId: String,
    val description: String?,
    val isShared: Boolean,
    val recurringSeriesId: String? = null
)

data class PendingIncome(
    val id: String,
    val amount: Long,
    val date: Long,
    val description: String?,
    val recurringSeriesId: String? = null,
    val categoryId: String? = null
)

data class ExistingRecurringExpenseItem(
    val id: String,
    val date: Long
)

data class ExistingRecurringIncomeItem(
    val id: String,
    val date: Long
)

const val RECURRING_MONTHLY_OCCURRENCES = 36
const val MAX_EXPENSE_INSTALLMENTS = 30

fun buildPendingExpenses(
    amount: Long,
    firstDate: Long,
    installments: Int,
    categoryId: String,
    description: String?,
    isShared: Boolean,
    idProvider: () -> String,
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): List<PendingExpense> {
    require(installments in 1..MAX_EXPENSE_INSTALLMENTS) { "installments must be between 1 and $MAX_EXPENSE_INSTALLMENTS" }

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
    amount: Long,
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

fun buildRecurringMonthlyExpensesFromExistingExpense(
    existingExpenseId: String,
    amount: Long,
    firstDate: Long,
    categoryId: String,
    description: String?,
    isShared: Boolean,
    recurringSeriesId: String,
    idProvider: () -> String,
    occurrences: Int = RECURRING_MONTHLY_OCCURRENCES,
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): List<PendingExpense> {
    return buildRecurringMonthlyExpenses(
        amount = amount,
        firstDate = firstDate,
        categoryId = categoryId,
        description = description,
        isShared = isShared,
        recurringSeriesId = recurringSeriesId,
        idProvider = idProvider,
        occurrences = occurrences,
        timeZone = timeZone
    ).mapIndexed { index, expense ->
        if (index == 0) {
            expense.copy(id = existingExpenseId)
        } else {
            expense
        }
    }
}

fun buildRecurringMonthlyIncomes(
    amount: Long,
    firstDate: Long,
    description: String?,
    categoryId: String?,
    recurringSeriesId: String,
    idProvider: () -> String,
    occurrences: Int = RECURRING_MONTHLY_OCCURRENCES,
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): List<PendingIncome> {
    require(occurrences > 0) { "occurrences must be greater than 0" }

    return List(occurrences) { index ->
        PendingIncome(
            id = idProvider(),
            amount = amount,
            date = monthlyOccurrenceDate(firstDate, index, timeZone),
            description = description.ifBlankToNull(),
            recurringSeriesId = recurringSeriesId,
            categoryId = categoryId
        )
    }
}

fun buildUpdatedRecurringExpenseSeries(
    existingItems: List<ExistingRecurringExpenseItem>,
    anchorItemId: String,
    anchorDate: Long,
    amount: Long,
    categoryId: String,
    description: String?,
    isShared: Boolean,
    recurringSeriesId: String,
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): List<PendingExpense> {
    val anchorItem = existingItems.firstOrNull { it.id == anchorItemId }
        ?: error("Anchor recurring expense item not found")

    return existingItems.map { item ->
        PendingExpense(
            id = item.id,
            amount = amount,
            date = monthlyOccurrenceDate(
                firstDate = anchorDate,
                monthOffset = monthDifference(anchorItem.date, item.date, timeZone),
                timeZone = timeZone
            ),
            categoryId = categoryId,
            description = description.ifBlankToNull(),
            isShared = isShared,
            recurringSeriesId = recurringSeriesId
        )
    }
}

fun buildUpdatedRecurringIncomeSeries(
    existingItems: List<ExistingRecurringIncomeItem>,
    anchorItemId: String,
    anchorDate: Long,
    amount: Long,
    description: String?,
    categoryId: String?,
    recurringSeriesId: String,
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): List<PendingIncome> {
    val anchorItem = existingItems.firstOrNull { it.id == anchorItemId }
        ?: error("Anchor recurring income item not found")

    return existingItems.map { item ->
        PendingIncome(
            id = item.id,
            amount = amount,
            date = monthlyOccurrenceDate(
                firstDate = anchorDate,
                monthOffset = monthDifference(anchorItem.date, item.date, timeZone),
                timeZone = timeZone
            ),
            description = description.ifBlankToNull(),
            recurringSeriesId = recurringSeriesId,
            categoryId = categoryId
        )
    }
}

fun splitAmountIntoInstallments(amount: Long, installments: Int): List<Long> {
    require(installments > 0) { "installments must be greater than 0" }

    val count = installments.toLong()
    val baseAmount = amount / count
    val remainder = (amount % count).toInt()

    return List(installments) { index ->
        if (index < remainder) addAmountsExact(baseAmount, 1L) else baseAmount
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

private fun monthDifference(
    firstDate: Long,
    secondDate: Long,
    timeZone: TimeZone
): Int {
    val firstLocalDate = Instant.fromEpochMilliseconds(firstDate)
        .toLocalDateTime(timeZone)
        .date
    val secondLocalDate = Instant.fromEpochMilliseconds(secondDate)
        .toLocalDateTime(timeZone)
        .date

    return (secondLocalDate.year - firstLocalDate.year) * 12 +
        (secondLocalDate.month.ordinal - firstLocalDate.month.ordinal)
}

private fun String?.ifBlankToNull(): String? = this?.takeIf { it.isNotBlank() }
