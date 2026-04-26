package it.homebudget.app.ui.screens

import android.app.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

actual class PlatformOptionPicker(
    private val showPicker: (String, List<String>, String?, (String) -> Unit) -> Unit
) {
    actual fun show(
        title: String,
        options: List<String>,
        selectedOption: String?,
        onOptionSelected: (String) -> Unit
    ) {
        showPicker(title, options, selectedOption, onOptionSelected)
    }
}

@Composable
actual fun rememberPlatformOptionPicker(): PlatformOptionPicker {
    val context = LocalContext.current

    return remember(context) {
        PlatformOptionPicker { title, options, _, onOptionSelected ->
            if (options.isEmpty()) return@PlatformOptionPicker

            AlertDialog.Builder(context)
                .setTitle(title)
                .setItems(options.toTypedArray()) { _, which ->
                    onOptionSelected(options[which])
                }
                .show()
        }
    }
}
