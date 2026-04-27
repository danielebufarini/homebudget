package it.homebudget.app.ui.screens

import kotlinx.datetime.LocalDate

internal fun formatExpenseDateGroupTitle(date: LocalDate): String {
    return "${date.day.toString().padStart(2, '0')} ${expenseMonthShortName(date.month.ordinal)} ${date.year}"
}

private fun expenseMonthShortName(monthOrdinal: Int): String {
    val names = listOf(
        "Jan",
        "Feb",
        "Mar",
        "Apr",
        "May",
        "Jun",
        "Jul",
        "Aug",
        "Sep",
        "Oct",
        "Nov",
        "Dec"
    )
    return names.getOrElse(monthOrdinal) { "" }
}
