package it.homebudget.app.ui.screens

import androidx.compose.runtime.Composable
import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.short_month_names
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringArrayResource
import kotlin.time.Instant

internal fun formatExpenseDateGroupTitle(date: LocalDate, shortMonthNames: List<String>): String {
    return "${date.day.toString().padStart(2, '0')} ${shortMonthNames[date.month.ordinal]} ${date.year}"
}

@Composable
internal fun formatExpenseDateGroupTitle(date: LocalDate): String {
    val shortMonthNames = stringArrayResource(Res.array.short_month_names)
    return formatExpenseDateGroupTitle(date, shortMonthNames)
}

internal fun epochMillisToLocalDate(epochMillis: Long): LocalDate {
    return Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
}

internal fun formatExpenseDate(epochMillis: Long): String {
    val date = epochMillisToLocalDate(epochMillis)
    return "${date.year}-${(date.month.ordinal + 1).toString().padStart(2, '0')}-${date.day.toString().padStart(2, '0')}"
}
