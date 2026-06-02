package it.danielebufarini.homebudget.database

import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

fun Long.toStoredYearMonth(
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): Int {
    val localDate = Instant.fromEpochMilliseconds(this)
        .toLocalDateTime(timeZone)
        .date
    return localDate.year * 100 + localDate.month.number
}

fun Long.toStoredLocalDate(
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): Int {
    val localDate = Instant.fromEpochMilliseconds(this)
        .toLocalDateTime(timeZone)
        .date
    return localDate.year * 10_000 + localDate.month.number * 100 + localDate.day
}

fun Long.toStoredDayOfMonth(
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): Int {
    return Instant.fromEpochMilliseconds(this)
        .toLocalDateTime(timeZone)
        .day
}
