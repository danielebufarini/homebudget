package it.homebudget.app.ui.screens

import androidx.compose.runtime.Composable

expect class PlatformDatePicker {
    fun show(initialDateMillis: Long?, onDateSelected: (Long) -> Unit)
}

@Composable
expect fun rememberPlatformDatePicker(): PlatformDatePicker
