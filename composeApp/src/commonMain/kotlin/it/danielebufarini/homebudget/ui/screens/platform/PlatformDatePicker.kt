package it.danielebufarini.homebudget.ui.screens.platform

import androidx.compose.runtime.Composable

expect class PlatformDatePicker {
    fun show(initialDateMillis: Long?, onDateSelected: (Long) -> Unit)

    @Composable
    fun Render()
}

@Composable
expect fun rememberPlatformDatePicker(): PlatformDatePicker
