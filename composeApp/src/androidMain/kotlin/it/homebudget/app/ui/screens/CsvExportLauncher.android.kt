package it.homebudget.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import it.homebudget.app.data.CsvExportFile
import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.data.exportBudgetItemsToCsv
import it.homebudget.app.localization.LocalStrings
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject
import kotlin.time.Clock
import kotlin.time.Instant

internal actual class CsvExportLauncher(
    private val onOpen: () -> Unit,
    private val renderContent: @Composable () -> Unit
) {
    actual fun open() {
        onOpen()
    }

    @Composable
    actual fun Render() {
        renderContent()
    }
}

private enum class ExportDateField {
    Start,
    End
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal actual fun rememberCsvExportLauncher(
    onExportMessage: (String) -> Unit
): CsvExportLauncher {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository: ExpenseRepository = koinInject()
    val strings = LocalStrings.current
    val today = remember { currentAndroidLocalDate() }
    val defaultStartDate = remember(today) { LocalDate(today.year, today.month, 1) }

    var showDialog by remember { mutableStateOf(false) }
    var startDate by remember { mutableStateOf(defaultStartDate) }
    var endDate by remember { mutableStateOf(today) }
    var activeDateField by remember { mutableStateOf<ExportDateField?>(null) }
    var pendingExport by remember { mutableStateOf<CsvExportFile?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        val exportFile = pendingExport
        pendingExport = null

        if (uri == null || exportFile == null) {
            return@rememberLauncherForActivityResult
        }

        scope.launch {
            val result = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(exportFile.content.encodeToByteArray())
                } ?: error(strings.csvExportFailed)
            }

            onExportMessage(
                if (result.isSuccess) strings.csvExportSaved else strings.csvExportFailed
            )
        }
    }

    return remember(
        context,
        repository,
        strings,
        showDialog,
        startDate,
        endDate,
        activeDateField,
        pendingExport
    ) {
        CsvExportLauncher(
            onOpen = { showDialog = true },
            renderContent = {
                if (showDialog) {
                    AlertDialog(
                        onDismissRequest = { showDialog = false },
                        title = { Text(strings.exportCsv) },
                        text = {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                ExportDateButton(
                                    label = strings.startDate,
                                    value = startDate.toString(),
                                    onClick = { activeDateField = ExportDateField.Start }
                                )

                                ExportDateButton(
                                    label = strings.endDate,
                                    value = endDate.toString(),
                                    onClick = { activeDateField = ExportDateField.End }
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(
                                colors = homeBudgetTextButtonColors(),
                                onClick = {
                                    if (startDate > endDate) {
                                        onExportMessage(strings.invalidDateRange)
                                        return@TextButton
                                    }

                                    scope.launch {
                                        val exportFile = runCatching {
                                            exportBudgetItemsToCsv(
                                                repository = repository,
                                                startDate = startDate,
                                                endDate = endDate
                                            )
                                        }.getOrElse {
                                            onExportMessage(strings.csvExportFailed)
                                            null
                                        }

                                        if (exportFile != null) {
                                            pendingExport = exportFile
                                            showDialog = false
                                            saveLauncher.launch(exportFile.fileName)
                                        }
                                    }
                                }
                            ) {
                                Text(strings.export)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { showDialog = false },
                                colors = homeBudgetTextButtonColors()
                            ) {
                                Text(strings.cancel)
                            }
                        }
                    )
                }

                activeDateField?.let { field ->
                    val initialDate = if (field == ExportDateField.Start) startDate else endDate
                    val pickerState = rememberDatePickerState(
                        initialSelectedDateMillis = initialDate.toEpochMillisUtc()
                    )

                    DatePickerDialog(
                        onDismissRequest = { activeDateField = null },
                        confirmButton = {
                            TextButton(
                                colors = homeBudgetTextButtonColors(),
                                onClick = {
                                    pickerState.selectedDateMillis
                                        ?.toAndroidLocalDate()
                                        ?.let { selectedDate ->
                                            when (field) {
                                                ExportDateField.Start -> startDate = selectedDate
                                                ExportDateField.End -> endDate = selectedDate
                                            }
                                        }
                                    activeDateField = null
                                }
                            ) {
                                Text(strings.save)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { activeDateField = null },
                                colors = homeBudgetTextButtonColors()
                            ) {
                                Text(strings.cancel)
                            }
                        }
                    ) {
                        DatePicker(
                            state = pickerState,
                            title = null,
                            headline = null,
                            showModeToggle = false
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun ExportDateButton(
    label: String,
    value: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        colors = homeBudgetOutlinedButtonColors(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

private fun currentAndroidLocalDate(): LocalDate {
    return Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
}

private fun LocalDate.toEpochMillisUtc(): Long {
    return java.util.GregorianCalendar(java.util.TimeZone.getTimeZone("UTC")).apply {
        set(java.util.Calendar.YEAR, year)
        set(java.util.Calendar.MONTH, month.ordinal)
        set(java.util.Calendar.DAY_OF_MONTH, day)
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun Long.toAndroidLocalDate(): LocalDate {
    return Instant.fromEpochMilliseconds(this)
        .toLocalDateTime(TimeZone.UTC)
        .date
}
