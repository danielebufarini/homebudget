package it.homebudget.app.ui.screens

import android.app.DatePickerDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Calendar

actual class PlatformDatePicker(
    private val showPicker: (Long?, (Long) -> Unit) -> Unit
) {
    actual fun show(initialDateMillis: Long?, onDateSelected: (Long) -> Unit) {
        showPicker(initialDateMillis, onDateSelected)
    }
}

@Composable
actual fun rememberPlatformDatePicker(): PlatformDatePicker {
    val context = LocalContext.current

    return remember(context) {
        PlatformDatePicker { initialDateMillis, onDateSelected ->
            val initialCalendar = Calendar.getInstance().apply {
                timeInMillis = initialDateMillis ?: System.currentTimeMillis()
            }

            DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    val pickedCalendar = Calendar.getInstance().apply {
                        set(Calendar.YEAR, year)
                        set(Calendar.MONTH, month)
                        set(Calendar.DAY_OF_MONTH, dayOfMonth)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    onDateSelected(pickedCalendar.timeInMillis)
                },
                initialCalendar.get(Calendar.YEAR),
                initialCalendar.get(Calendar.MONTH),
                initialCalendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }
}
