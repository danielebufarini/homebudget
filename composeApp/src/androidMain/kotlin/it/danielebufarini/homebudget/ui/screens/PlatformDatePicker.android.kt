package it.danielebufarini.homebudget.ui.screens.platform

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.cancel
import homebudget.composeapp.generated.resources.save
import homebudget.composeapp.generated.resources.select_date
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Instant

private data class AndroidDatePickerRequest(
    val initialDateMillis: Long?,
    val onDateSelected: (Long) -> Unit
)

actual class PlatformDatePicker {
    private var request by mutableStateOf<AndroidDatePickerRequest?>(null)

    actual fun show(initialDateMillis: Long?, onDateSelected: (Long) -> Unit) {
        request = AndroidDatePickerRequest(initialDateMillis, onDateSelected)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    actual fun Render() {
        val activeRequest = request ?: return
        val saveLabel = stringResource(Res.string.save)
        val cancelLabel = stringResource(Res.string.cancel)
        val selectDateLabel = stringResource(Res.string.select_date)
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = activeRequest.initialDateMillis
                ?: Clock.System.now().toEpochMilliseconds().toStartOfDayMillis()
        )

        DatePickerDialog(
            onDismissRequest = { request = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { selectedDate ->
                            request = null
                            activeRequest.onDateSelected(selectedDate.toStartOfDayMillis())
                        }
                    }
                ) {
                    Text(saveLabel)
                }
            },
            dismissButton = {
                TextButton(onClick = { request = null }) {
                    Text(cancelLabel)
                }
            }
        ) {
            DatePicker(
                state = pickerState,
                title = {
                    Text(
                        text = selectDateLabel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 24.dp, top = 18.dp),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                headline = null,
                showModeToggle = false
            )
        }
    }
}

@Composable
actual fun rememberPlatformDatePicker(): PlatformDatePicker {
    return remember { PlatformDatePicker() }
}

private fun Long.toStartOfDayMillis(): Long {
    val localDate = Instant.fromEpochMilliseconds(this)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
    return localDate
        .atStartOfDayIn(TimeZone.currentSystemDefault())
        .toEpochMilliseconds()
}
