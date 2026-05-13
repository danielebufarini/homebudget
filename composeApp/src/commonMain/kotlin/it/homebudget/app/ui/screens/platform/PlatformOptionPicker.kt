package it.homebudget.app.ui.screens

import androidx.compose.runtime.Composable

expect class PlatformOptionPicker {
    fun show(
        title: String,
        options: List<String>,
        selectedOption: String? = null,
        onOptionSelected: (String) -> Unit
    )
}

@Composable
expect fun rememberPlatformOptionPicker(): PlatformOptionPicker
