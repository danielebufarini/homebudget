package it.homebudget.app.ui.screens

import kotlinx.datetime.LocalDate

internal fun formatExpenseDateGroupTitle(date: LocalDate): String {
    return "${date.day.toString().padStart(2, '0')} ${shortMonthName(date.month.ordinal + 1)} ${date.year}"
}
