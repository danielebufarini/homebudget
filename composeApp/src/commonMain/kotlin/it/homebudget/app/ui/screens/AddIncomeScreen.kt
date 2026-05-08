package it.homebudget.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import com.ionspin.kotlin.bignum.integer.BigInteger
import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.add_income
import homebudget.composeapp.generated.resources.amount
import homebudget.composeapp.generated.resources.back
import homebudget.composeapp.generated.resources.cancel
import homebudget.composeapp.generated.resources.date
import homebudget.composeapp.generated.resources.delete_income
import homebudget.composeapp.generated.resources.delete_recurring_income_title
import homebudget.composeapp.generated.resources.description
import homebudget.composeapp.generated.resources.edit_income
import homebudget.composeapp.generated.resources.enter_valid_amount
import homebudget.composeapp.generated.resources.recurring_income_action_delete
import homebudget.composeapp.generated.resources.recurring_income_action_update
import homebudget.composeapp.generated.resources.recurring_income_info
import homebudget.composeapp.generated.resources.recurring_income_series_info
import homebudget.composeapp.generated.resources.recurring_monthly
import homebudget.composeapp.generated.resources.save
import homebudget.composeapp.generated.resources.short_month_names
import homebudget.composeapp.generated.resources.unable_to_delete_income
import homebudget.composeapp.generated.resources.unable_to_save_income
import homebudget.composeapp.generated.resources.update
import homebudget.composeapp.generated.resources.update_recurring_income_title
import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.data.IdGenerator
import it.homebudget.app.data.PendingIncome
import it.homebudget.app.data.RECURRING_MONTHLY_OCCURRENCES
import it.homebudget.app.data.buildRecurringMonthlyIncomes
import it.homebudget.app.data.formatAmountInput
import it.homebudget.app.data.parseAmountInput
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.time.Clock
import kotlin.time.Instant

private data class PendingRecurringIncomeUpdate(
    val amount: BigInteger,
    val date: Long,
    val description: String?
)

private enum class RecurringIncomeAction {
    Update,
    Delete
}

class AddIncomeScreen(
    private val incomeId: String? = null,
    private val initialYear: Int? = null,
    private val initialMonth: Int? = null
) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        RouteContent(
            showNavigationChrome = true,
            onClose = { navigator?.pop() }
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun RouteContent(
        showNavigationChrome: Boolean,
        onClose: () -> Unit
    ) {
        val repository: ExpenseRepository = koinInject()
        val isIos = rememberIsIosPlatform()
        val platformDatePicker = rememberPlatformDatePicker()
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }
        val addIncomeLabel = stringResource(Res.string.add_income)
        val amountLabel = stringResource(Res.string.amount)
        val backLabel = stringResource(Res.string.back)
        val cancelLabel = stringResource(Res.string.cancel)
        val dateLabel = stringResource(Res.string.date)
        val deleteIncomeLabel = stringResource(Res.string.delete_income)
        val deleteRecurringIncomeTitle = stringResource(Res.string.delete_recurring_income_title)
        val descriptionLabel = stringResource(Res.string.description)
        val editIncomeLabel = stringResource(Res.string.edit_income)
        val enterValidAmountLabel = stringResource(Res.string.enter_valid_amount)
        val recurringIncomeActionDelete = stringResource(Res.string.recurring_income_action_delete)
        val recurringIncomeActionUpdate = stringResource(Res.string.recurring_income_action_update)
        val recurringIncomeInfo = stringResource(
            Res.string.recurring_income_info,
            RECURRING_MONTHLY_OCCURRENCES / 12
        )
        val recurringIncomeSeriesInfo = stringResource(Res.string.recurring_income_series_info)
        val recurringMonthlyLabel = stringResource(Res.string.recurring_monthly)
        val saveLabel = stringResource(Res.string.save)
        val unableToDeleteIncomeLabel = stringResource(Res.string.unable_to_delete_income)
        val unableToSaveIncomeLabel = stringResource(Res.string.unable_to_save_income)
        val updateLabel = stringResource(Res.string.update)
        val updateRecurringIncomeTitle = stringResource(Res.string.update_recurring_income_title)
        val defaultDateMillis = remember(incomeId, initialYear, initialMonth) {
            if (incomeId != null) {
                null
            } else {
                buildInitialIncomeDateMillis(
                    year = initialYear,
                    month = initialMonth
                )
            }
        }

        var amount by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var selectedDateMillis by remember(incomeId, defaultDateMillis) {
            mutableStateOf(defaultDateMillis ?: Clock.System.now().toEpochMilliseconds())
        }
        var isRecurringMonthly by remember { mutableStateOf(false) }
        var recurringSeriesId by remember { mutableStateOf<String?>(null) }
        var isSaving by remember { mutableStateOf(false) }
        var isInitialized by remember(incomeId) { mutableStateOf(incomeId == null) }
        var pendingRecurringUpdate by remember { mutableStateOf<PendingRecurringIncomeUpdate?>(null) }
        var pendingRecurringAction by remember { mutableStateOf<RecurringIncomeAction?>(null) }

        LaunchedEffect(incomeId) {
            if (incomeId == null || isInitialized) {
                return@LaunchedEffect
            }

            val income = repository.getIncomeById(incomeId) ?: return@LaunchedEffect
            amount = formatAmountInput(income.amount)
            description = income.description.orEmpty()
            selectedDateMillis = income.date
            recurringSeriesId = income.recurringSeriesId
            isInitialized = true
        }

        fun dismissRecurringDialog() {
            pendingRecurringUpdate = null
            pendingRecurringAction = null
        }

        fun closeAfterRecurringAction() {
            dismissRecurringDialog()
            onClose()
        }

        suspend fun saveIncomeUpdate(
            updateWholeSeries: Boolean,
            payload: PendingRecurringIncomeUpdate
        ) {
            runCatching {
                val currentIncomeId = incomeId ?: return@runCatching
                val seriesId = recurringSeriesId
                if (updateWholeSeries && !seriesId.isNullOrBlank()) {
                    repository.updateRecurringIncomeSeries(
                        anchorIncomeId = currentIncomeId,
                        seriesId = seriesId,
                        amount = payload.amount,
                        date = payload.date,
                        description = payload.description
                    )
                } else {
                    repository.insertIncomes(
                        incomes = listOf(
                            PendingIncome(
                                id = currentIncomeId,
                                amount = payload.amount,
                                date = payload.date,
                                description = payload.description,
                                recurringSeriesId = recurringSeriesId
                            )
                        )
                    )
                }
            }.onSuccess {
                closeAfterRecurringAction()
            }.onFailure {
                snackbarHostState.showSnackbar(unableToSaveIncomeLabel)
            }
            isSaving = false
        }

        suspend fun deleteIncome(deleteWholeSeries: Boolean) {
            runCatching {
                val currentIncomeId = incomeId ?: return@runCatching
                val seriesId = recurringSeriesId
                if (deleteWholeSeries && !seriesId.isNullOrBlank()) {
                    repository.deleteRecurringIncomeSeries(seriesId)
                } else {
                    repository.deleteIncome(currentIncomeId)
                }
            }.onSuccess {
                closeAfterRecurringAction()
            }.onFailure {
                snackbarHostState.showSnackbar(unableToDeleteIncomeLabel)
            }
            isSaving = false
        }

        fun requestDeleteIncome() {
            if (recurringSeriesId != null) {
                pendingRecurringAction = RecurringIncomeAction.Delete
            } else {
                scope.launch {
                    isSaving = true
                    deleteIncome(deleteWholeSeries = false)
                }
            }
        }

        Scaffold(
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            },
            topBar = {
                if (showNavigationChrome) {
                    TopAppBar(
                        title = {
                            Text(if (incomeId == null) addIncomeLabel else editIncomeLabel)
                        },
                        navigationIcon = {
                            if (isIos) {
                                TextButton(onClick = onClose) {
                                    Text(backLabel)
                                }
                            }
                        }
                    )
                }
            },
            floatingActionButton = {
                if (!isIos && incomeId != null) {
                    DeleteEditItemFab(
                        label = deleteIncomeLabel,
                        enabled = !isSaving,
                        onClick = ::requestDeleteIncome
                    )
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .padding(bottom = if (!isIos && incomeId != null) 88.dp else 0.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PlatformTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = amountLabel,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                PlatformTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = descriptionLabel,
                    modifier = Modifier.fillMaxWidth()
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            platformDatePicker.show(selectedDateMillis) { pickedDate ->
                                selectedDateMillis = pickedDate
                            }
                        }
                ) {
                    PlatformTextField(
                        value = selectedDateMillis.formatDateLabel(),
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = dateLabel,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (incomeId == null) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isIos) {
                                Switch(
                                    checked = isRecurringMonthly,
                                    onCheckedChange = { isRecurringMonthly = it }
                                )
                            } else {
                                Checkbox(
                                    checked = isRecurringMonthly,
                                    onCheckedChange = { isRecurringMonthly = it }
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(recurringMonthlyLabel)
                        }
                        if (isRecurringMonthly) {
                            Text(
                                text = recurringIncomeInfo,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                if (recurringSeriesId != null) {
                    RecurringSeriesNotice(
                        text = recurringIncomeSeriesInfo
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onClose,
                        colors = homeBudgetButtonColors(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(cancelLabel)
                    }

                    Button(
                        enabled = !isSaving,
                        colors = homeBudgetButtonColors(),
                        onClick = {
                            scope.launch {
                                val parsedAmount = parseAmountInput(amount)

                                when {
                                    parsedAmount == null || parsedAmount <= BigInteger.ZERO -> {
                                        snackbarHostState.showSnackbar(enterValidAmountLabel)
                                    }
                                    else -> {
                                        isSaving = true
                                        val normalizedDescription = description.trim().ifBlank { null }
                                        if (incomeId != null && recurringSeriesId != null) {
                                            pendingRecurringUpdate = PendingRecurringIncomeUpdate(
                                                amount = parsedAmount,
                                                date = selectedDateMillis,
                                                description = normalizedDescription
                                            )
                                            pendingRecurringAction = RecurringIncomeAction.Update
                                            isSaving = false
                                        } else {
                                            runCatching {
                                                val incomes = if (incomeId == null) {
                                                    if (isRecurringMonthly) {
                                                        buildRecurringMonthlyIncomes(
                                                            amount = parsedAmount,
                                                            firstDate = selectedDateMillis,
                                                            description = description.trim(),
                                                            recurringSeriesId = buildRecurringIncomeSeriesId(),
                                                            idProvider = ::buildIncomeId
                                                        )
                                                    } else {
                                                        listOf(
                                                            PendingIncome(
                                                                id = buildIncomeId(),
                                                                amount = parsedAmount,
                                                                date = selectedDateMillis,
                                                                description = normalizedDescription,
                                                                recurringSeriesId = null
                                                            )
                                                        )
                                                    }
                                                } else {
                                                    listOf(
                                                        PendingIncome(
                                                            id = incomeId,
                                                            amount = parsedAmount,
                                                            date = selectedDateMillis,
                                                            description = normalizedDescription,
                                                            recurringSeriesId = recurringSeriesId
                                                        )
                                                    )
                                                }
                                                repository.insertIncomes(incomes = incomes)
                                            }.onSuccess {
                                                onClose()
                                            }.onFailure {
                                                snackbarHostState.showSnackbar(unableToSaveIncomeLabel)
                                            }
                                            isSaving = false
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (incomeId == null) saveLabel else updateLabel)
                    }
                }
            }
        }

        if (pendingRecurringAction != null) {
            RecurringSeriesActionDialog(
                title = when (pendingRecurringAction) {
                    RecurringIncomeAction.Update -> updateRecurringIncomeTitle
                    RecurringIncomeAction.Delete -> deleteRecurringIncomeTitle
                    null -> ""
                },
                message = when (pendingRecurringAction) {
                    RecurringIncomeAction.Update -> recurringIncomeActionUpdate
                    RecurringIncomeAction.Delete -> recurringIncomeActionDelete
                    null -> ""
                },
                onThisInstanceOnly = {
                    scope.launch {
                        isSaving = true
                        when (pendingRecurringAction) {
                            RecurringIncomeAction.Update -> {
                                val payload = pendingRecurringUpdate
                                if (payload == null) {
                                    isSaving = false
                                } else {
                                    saveIncomeUpdate(
                                        updateWholeSeries = false,
                                        payload = payload
                                    )
                                }
                            }
                            RecurringIncomeAction.Delete -> {
                                deleteIncome(deleteWholeSeries = false)
                            }
                            null -> {
                                isSaving = false
                            }
                        }
                    }
                },
                onWholeSeries = {
                    scope.launch {
                        isSaving = true
                        when (pendingRecurringAction) {
                            RecurringIncomeAction.Update -> {
                                val payload = pendingRecurringUpdate
                                if (payload == null) {
                                    isSaving = false
                                } else {
                                    saveIncomeUpdate(
                                        updateWholeSeries = true,
                                        payload = payload
                                    )
                                }
                            }
                            RecurringIncomeAction.Delete -> {
                                deleteIncome(deleteWholeSeries = true)
                            }
                            null -> {
                                isSaving = false
                            }
                        }
                    }
                },
                onDismiss = {
                    dismissRecurringDialog()
                    isSaving = false
                }
            )
        }
    }
}

private fun buildIncomeId(): String = IdGenerator.newId("income")

private fun buildRecurringIncomeSeriesId(): String = IdGenerator.newId("recurring-income")

private fun buildInitialIncomeDateMillis(
    year: Int?,
    month: Int?
): Long {
    val timeZone = TimeZone.currentSystemDefault()
    val now = Clock.System.now().toLocalDateTime(timeZone).date
    val targetYear = year ?: now.year
    val targetMonth = month ?: (now.month.ordinal + 1)
    val dayOfMonth = now.day.coerceAtMost(daysInMonth(targetYear, targetMonth))
    return LocalDate(
        year = targetYear,
        month = targetMonth,
        day = dayOfMonth
    ).atStartOfDayIn(timeZone).toEpochMilliseconds()
}

private fun daysInMonth(year: Int, month: Int): Int {
    return when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if (isLeapYear(year)) 29 else 28
        else -> 31
    }
}

private fun isLeapYear(year: Int): Boolean {
    return (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
}

@Composable
private fun Long.formatDateLabel(): String {
    val shortMonthNames = stringArrayResource(Res.array.short_month_names)
    val date = Instant.fromEpochMilliseconds(this)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
    return "${date.day.toString().padStart(2, '0')} ${shortMonthNames[date.month.ordinal]} ${date.year}"
}
