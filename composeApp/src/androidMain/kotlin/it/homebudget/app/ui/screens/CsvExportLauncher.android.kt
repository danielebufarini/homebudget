package it.homebudget.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import homebudget.composeapp.generated.resources.*
import it.homebudget.app.data.CsvExportFile
import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.data.exportBudgetItemsToCsv
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import org.jetbrains.compose.resources.stringResource
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
    val exportCsvLabel = stringResource(Res.string.export_csv)
    val fromLabel = stringResource(Res.string.from)
    val toLabel = stringResource(Res.string.to)
    val cancelLabel = stringResource(Res.string.cancel)
    val saveLabel = stringResource(Res.string.save)
    val invalidDateRangeLabel = stringResource(Res.string.invalid_date_range)
    val csvExportFailedLabel = stringResource(Res.string.csv_export_failed)
    val csvExportSavedLabel = stringResource(Res.string.csv_export_saved)
    val today = remember { currentAndroidLocalDate() }
    val defaultStartDate = remember(today) { today.minus(DatePeriod(days = 29)) }

    var showDialog by remember { mutableStateOf(false) }
    var startDate by rememberSaveable { mutableStateOf(defaultStartDate) }
    var endDate by rememberSaveable { mutableStateOf(today) }
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
                } ?: error(csvExportFailedLabel)
            }

            onExportMessage(
                if (result.isSuccess) csvExportSavedLabel else csvExportFailedLabel
            )
        }
    }

    return remember(
        context,
        repository,
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
                    ModalBottomSheet(
                        onDismissRequest = { showDialog = false }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp)
                                .padding(bottom = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                ExportDateButton(
                                    label = fromLabel,
                                    value = startDate.toString(),
                                    onClick = { activeDateField = ExportDateField.Start }
                                )

                                ExportDateButton(
                                    label = toLabel,
                                    value = endDate.toString(),
                                    onClick = { activeDateField = ExportDateField.End }
                                )
                            }

                            Button(
                                onClick = {
                                    if (startDate > endDate) {
                                        onExportMessage(invalidDateRangeLabel)
                                        return@Button
                                    }

                                    scope.launch {
                                        val exportFile = runCatching {
                                            exportBudgetItemsToCsv(
                                                repository = repository,
                                                startDate = startDate,
                                                endDate = endDate
                                            )
                                        }.getOrElse {
                                            onExportMessage(csvExportFailedLabel)
                                            null
                                        }

                                        if (exportFile != null) {
                                            pendingExport = exportFile
                                            showDialog = false
                                            saveLauncher.launch(exportFile.fileName)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = homeBudgetButtonColors()
                            ) {
                                Text(exportCsvLabel)
                            }

                            TextButton(
                                onClick = { showDialog = false },
                                modifier = Modifier.fillMaxWidth(),
                                colors = homeBudgetTextButtonColors()
                            ) {
                                Text(cancelLabel)
                            }
                        }
                    }
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
                                Text(saveLabel)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { activeDateField = null },
                                colors = homeBudgetTextButtonColors()
                            ) {
                                Text(cancelLabel)
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
