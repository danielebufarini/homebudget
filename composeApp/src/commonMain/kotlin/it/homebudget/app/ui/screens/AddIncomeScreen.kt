package it.homebudget.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.data.formatAmountInput
import it.homebudget.app.data.parseAmountInput
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
                        value = selectedDateMillis?.formatDateLabel().orEmpty(),
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = "Date",
                        modifier = Modifier.fillMaxWidth()
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
                        Text("Cancel")
                    }

                    Button(
                        enabled = !isSaving,
                        colors = homeBudgetButtonColors(),
                        onClick = {
                            scope.launch {
                                val parsedAmount = parseAmountInput(amount)

                                when {
                                    parsedAmount == null || parsedAmount <= com.ionspin.kotlin.bignum.integer.BigInteger.ZERO -> {
                                        snackbarHostState.showSnackbar("Enter a valid amount greater than 0")
                                    }
                                    else -> {
                                        isSaving = true
                                        runCatching {
                                            repository.insertIncome(
                                                id = incomeId ?: buildIncomeId(),
                                                amount = parsedAmount,
                                                date = selectedDateMillis,
                                                description = description.trim().ifBlank { null }
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
