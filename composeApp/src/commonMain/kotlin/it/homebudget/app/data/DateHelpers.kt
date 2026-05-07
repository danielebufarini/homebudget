package it.homebudget.app.data

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

data class MonthKey(
    val year: Int,
    val month: Int
)

fun monthBounds(
    year: Int,
    month: Int,
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): Pair<Long, Long> {
    val startDate = LocalDate(
        year = year,
        monthNumber = month,
        dayOfMonth = 1
    )

    val endDate = startDate.plus(1, DateTimeUnit.MONTH)

    val startMillis = startDate
        .atStartOfDayIn(timeZone)
        .toEpochMilliseconds()

    val endMillis = endDate
        .atStartOfDayIn(timeZone)
        .toEpochMilliseconds()

    return startMillis to endMillis
}

fun Long.toMonthKey(
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): MonthKey {
    val localDate = Instant.fromEpochMilliseconds(this)
        .toLocalDateTime(timeZone)
        .date

    return MonthKey(
        year = localDate.year,
        month = localDate.monthNumber
    )
}

fun MonthKey.toStartOfMonthMillis(
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): Long {
    return LocalDate(
        year = year,
        monthNumber = month,
        dayOfMonth = 1
    )
        .atStartOfDayIn(timeZone)
        .toEpochMilliseconds()
}

fun Long.toDayOfMonth(
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): Int {
    return Instant.fromEpochMilliseconds(this)
        .toLocalDateTime(timeZone)
        .dayOfMonth
}