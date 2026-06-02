package it.danielebufarini.homebudget.ui.screens.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import it.danielebufarini.homebudget.ui.screens.requestIosNativeDatePicker

actual class PlatformDatePicker {
    actual fun show(initialDateMillis: Long?, onDateSelected: (Long) -> Unit) {
        requestIosNativeDatePicker(
            initialDateMillis = initialDateMillis,
            onDateSelected = onDateSelected
        )
    }

    @Composable
    actual fun Render() = Unit
}

@Composable
actual fun rememberPlatformDatePicker(): PlatformDatePicker {
    return remember { PlatformDatePicker() }
}
