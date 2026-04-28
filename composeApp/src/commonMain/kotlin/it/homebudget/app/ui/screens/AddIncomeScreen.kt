package it.homebudget.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import com.ionspin.kotlin.bignum.integer.BigInteger
import it.homebudget.app.data.*
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Instant

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

    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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

        Scaffold(
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            },
            topBar = {
                if (showNavigationChrome) {
                    TopAppBar(
                        title = {
                            Text(if (incomeId == null) "Add Income" else "Edit Income")
                        },
                        navigationIcon = {
                            if (isIos) {
                                TextButton(onClick = onClose) {
                                    Text("back")
                                }
                            }
                        }
                    )
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PlatformTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = "Amount",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                PlatformTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = "Description",
                    modifier = Modifier.fillMaxWidth()
                )

                androidx.compose.foundation.layout.Box(
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
                        label = "Date",
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
                            Text("Recurring Monthly")
                        }
                        if (isRecurringMonthly) {
                            Text(
                                text = "Creates the same income every month on this day for the next ${RECURRING_MONTHLY_OCCURRENCES / 12} years.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                if (recurringSeriesId != null) {
                    Text(
                        text = "This income is part of a recurring monthly series.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (recurringSeriesId != null) {
                    Button(
                        onClick = {
                            scope.launch {
                                isSaving = true
                                runCatching {
                                    repository.cancelRecurringIncomes(
                                        seriesId = recurringSeriesId.orEmpty(),
                                        fromDate = selectedDateMillis
                                    )
                                }.onSuccess {
                                    onClose()
                                }.onFailure {
                                    snackbarHostState.showSnackbar("Unable to cancel recurring income")
                                }
                                isSaving = false
                            }
                        },
                        colors = homeBudgetButtonColors(),
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel recurring from this month")
                    }
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
                        Text("Cancel")
                    }

                    Button(
                        enabled = !isSaving,
                        colors = homeBudgetButtonColors(),
                        onClick = {
                            scope.launch {
                                val parsedAmount = parseAmountInput(amount)

                                when {
                                    parsedAmount == null || parsedAmount <= BigInteger.ZERO -> {
                                        snackbarHostState.showSnackbar("Enter a valid amount greater than 0")
                                    }
                                    else -> {
                                        isSaving = true
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
                                                            description = description.trim().ifBlank { null },
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
                                                        description = description.trim().ifBlank { null },
                                                        recurringSeriesId = recurringSeriesId
                                                    )
                                                )
                                            }
                                            repository.insertIncomes(
                                                incomes = incomes
                                            )
                                        }.onSuccess {
                                            onClose()
                                        }.onFailure {
                                            snackbarHostState.showSnackbar("Unable to save income")
                                        }
                                        isSaving = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (incomeId == null) "Save" else "Update")
                    }
                }
            }
        }
    }
}

private fun buildIncomeId(): String {
    return "income_${Clock.System.now().toEpochMilliseconds()}_${Random.nextInt(1_000, 9_999)}"
}

private fun buildRecurringIncomeSeriesId(): String {
    return "recurring-income_${Clock.System.now().toEpochMilliseconds()}_${Random.nextInt(1_000, 9_999)}"
}

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
        monthNumber = targetMonth,
        dayOfMonth = dayOfMonth
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

private fun Long.formatDateLabel(): String {
    val date = Instant.fromEpochMilliseconds(this)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
    val monthNames = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )
    return "${date.day.toString().padStart(2, '0')} ${monthNames[date.month.ordinal]} ${date.year}"
}
