package it.homebudget.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import it.homebudget.app.data.PendingExpense
import it.homebudget.app.data.RECURRING_MONTHLY_OCCURRENCES
import it.homebudget.app.data.buildRecurringMonthlyExpenses
import it.homebudget.app.data.buildPendingExpenses
import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.data.formatAmountInput
import it.homebudget.app.data.parseAmountInput
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Instant

class AddExpenseScreen(
    private val expenseId: String? = null,
    private val readOnly: Boolean = false
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
        val platformOptionPicker = rememberPlatformOptionPicker()
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }

        var amount by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("") }
        var selectedDateMillis by remember { mutableStateOf<Long?>(Clock.System.now().toEpochMilliseconds()) }
        var installmentCount by remember { mutableStateOf(1) }
        var isRecurringMonthly by remember { mutableStateOf(false) }
        var recurringSeriesId by remember { mutableStateOf<String?>(null) }
        var isShared by remember { mutableStateOf(false) }
        var initialIsShared by remember { mutableStateOf(false) }
        var categoryExpanded by remember { mutableStateOf(false) }
        var installmentExpanded by remember { mutableStateOf(false) }
        var isSaving by remember { mutableStateOf(false) }
        var isInitialized by remember(expenseId) { mutableStateOf(expenseId == null) }

        val categories by repository.getAllCategories().collectAsState(initial = emptyList())
        val selectedCategory = categories.find { it.name == category }
        val installmentOptions = remember { (1..10).toList() }
        val installmentLabels = remember(installmentOptions) {
            installmentOptions.associateWith { count ->
                if (count == 1) "Single payment" else "$count installments"
            }
        }

        LaunchedEffect(repository) {
            repository.insertDefaultCategoriesIfEmpty()
        }

        LaunchedEffect(expenseId, categories) {
            if (expenseId == null || isInitialized) {
                return@LaunchedEffect
            }

            val expense = repository.getExpenseById(expenseId) ?: return@LaunchedEffect
            amount = formatAmountInput(expense.amount)
            description = expense.description.orEmpty()
            category = categories.find { it.id == expense.categoryId }?.name.orEmpty()
            selectedDateMillis = expense.date
            recurringSeriesId = expense.recurringSeriesId
            isShared = expense.isShared == 1L
            initialIsShared = expense.isShared == 1L
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
                            Text(
                                when {
                                    readOnly -> "Expense Details"
                                    expenseId == null -> "Add Expense"
                                    else -> "Edit Expense"
                                }
                            )
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
                    onValueChange = { if (!readOnly) amount = it },
                    label = "Amount",
                    readOnly = readOnly,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                PlatformTextField(
                    value = description,
                    onValueChange = { if (!readOnly) description = it },
                    label = "Description",
                    readOnly = readOnly,
                    modifier = Modifier.fillMaxWidth()
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !readOnly) {
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

                if (isIos) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !readOnly && categories.isNotEmpty()) {
                                platformOptionPicker.show(
                                    title = "Select Category",
                                    options = categories.map { it.name },
                                    selectedOption = category.ifBlank { null }
                                ) { selectedOption ->
                                    category = selectedOption
                                }
                            }
                    ) {
                        PlatformTextField(
                            value = category,
                            onValueChange = {},
                            readOnly = true,
                            enabled = false,
                            label = "Category",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    ExposedDropdownMenuBox(
                        expanded = categoryExpanded && !readOnly,
                        onExpandedChange = {
                            if (!readOnly) {
                                categoryExpanded = !categoryExpanded
                            }
                        }
                    ) {
                        PlatformTextField(
                            value = category,
                            onValueChange = {},
                            readOnly = true,
                            label = "Category",
                            trailingIcon = {
                                if (!readOnly) {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded)
                                }
                            },
                            enabled = !readOnly,
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = categoryExpanded && !readOnly,
                            onDismissRequest = { categoryExpanded = false }
                        ) {
                            categories.forEach { selectionOption ->
                                DropdownMenuItem(
                                    text = { Text(selectionOption.name) },
                                    onClick = {
                                        category = selectionOption.name
                                        categoryExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                if (expenseId == null && !isRecurringMonthly) {
                    if (isIos) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !readOnly) {
                                    val options = installmentOptions.map { installmentLabels.getValue(it) }
                                    platformOptionPicker.show(
                                        title = "Select Installments",
                                        options = options,
                                        selectedOption = installmentLabels.getValue(installmentCount)
                                    ) { selectedOption ->
                                        installmentCount = installmentOptions.first { option ->
                                            installmentLabels.getValue(option) == selectedOption
                                        }
                                    }
                                }
                        ) {
                            PlatformTextField(
                                value = installmentLabels.getValue(installmentCount),
                                onValueChange = {},
                                readOnly = true,
                                enabled = false,
                                label = "Installments",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        ExposedDropdownMenuBox(
                            expanded = installmentExpanded && !readOnly,
                            onExpandedChange = {
                                if (!readOnly) {
                                    installmentExpanded = !installmentExpanded
                                }
                            }
                        ) {
                            PlatformTextField(
                                value = installmentLabels.getValue(installmentCount),
                                onValueChange = {},
                                readOnly = true,
                                label = "Installments",
                                trailingIcon = {
                                    if (!readOnly) {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = installmentExpanded)
                                    }
                                },
                                enabled = !readOnly,
                                modifier = Modifier
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = installmentExpanded && !readOnly,
                                onDismissRequest = { installmentExpanded = false }
                            ) {
                                installmentOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(installmentLabels.getValue(option)) },
                                        onClick = {
                                            installmentCount = option
                                            installmentExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                if (expenseId == null) {
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
                                    onCheckedChange = {
                                        isRecurringMonthly = it
                                        if (it) {
                                            installmentCount = 1
                                        }
                                    },
                                    enabled = !readOnly
                                )
                            } else {
                                androidx.compose.material3.Checkbox(
                                    checked = isRecurringMonthly,
                                    onCheckedChange = {
                                        isRecurringMonthly = it
                                        if (it) {
                                            installmentCount = 1
                                        }
                                    },
                                    enabled = !readOnly
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("Recurring Monthly")
                        }
                        if (isRecurringMonthly) {
                            Text(
                                text = "Creates the same expense every month on this day for the next ${RECURRING_MONTHLY_OCCURRENCES / 12} years.",
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                if (recurringSeriesId != null) {
                    Text(
                        text = "This expense is part of a recurring monthly series.",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isIos) {
                        Switch(
                            checked = isShared,
                            onCheckedChange = { if (!readOnly) isShared = it },
                            enabled = !readOnly
                        )
                    } else {
                        androidx.compose.material3.Checkbox(
                            checked = isShared,
                            onCheckedChange = { if (!readOnly) isShared = it },
                            enabled = !readOnly
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("Shared Expense")
                }

                if (readOnly) {
                    TextButton(
                        onClick = onClose,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Close")
                    }
                } else {
                    if (recurringSeriesId != null && selectedDateMillis != null) {
                        Button(
                            onClick = {
                                scope.launch {
                                    isSaving = true
                                    runCatching {
                                        repository.cancelRecurringExpenses(
                                            seriesId = recurringSeriesId.orEmpty(),
                                            fromDate = selectedDateMillis ?: return@runCatching
                                        )
                                    }.onSuccess {
                                        onClose()
                                    }.onFailure {
                                        snackbarHostState.showSnackbar("Unable to cancel recurring expense")
                                    }
                                    isSaving = false
                                }
                            },
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
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }

                        Button(
                            enabled = !isSaving,
                            onClick = {
                                scope.launch {
                                    val parsedAmount = parseAmountInput(amount)
                                    val expenseDate = selectedDateMillis

                                    when {
                                        parsedAmount == null || parsedAmount <= BigInteger.ZERO -> {
                                            snackbarHostState.showSnackbar("Enter a valid amount greater than 0")
                                        }
                                        selectedCategory == null -> {
                                            snackbarHostState.showSnackbar("Select a category")
                                        }
                                        expenseDate == null -> {
                                            snackbarHostState.showSnackbar("Select a date")
                                        }
                                        else -> {
                                            isSaving = true
                                            runCatching {
                                                val expenses = if (expenseId == null) {
                                                    if (isRecurringMonthly) {
                                                        buildRecurringMonthlyExpenses(
                                                            amount = parsedAmount,
                                                            firstDate = expenseDate,
                                                            categoryId = selectedCategory.id,
                                                            description = description,
                                                            isShared = isShared,
                                                            recurringSeriesId = buildRecurringSeriesId(),
                                                            idProvider = ::buildExpenseId
                                                        )
                                                    } else {
                                                        buildPendingExpenses(
                                                            amount = parsedAmount,
                                                            firstDate = expenseDate,
                                                            installments = installmentCount,
                                                            categoryId = selectedCategory.id,
                                                            description = description,
                                                            isShared = isShared,
                                                            idProvider = ::buildExpenseId
                                                        )
                                                    }
                                                } else {
                                                    recurringSeriesId?.takeIf { initialIsShared != isShared }?.let { seriesId ->
                                                        repository.updateRecurringExpenseShared(
                                                            seriesId = seriesId,
                                                            isShared = isShared
                                                        )
                                                    }
                                                    listOf(
                                                        PendingExpense(
                                                            id = expenseId,
                                                            amount = parsedAmount,
                                                            date = expenseDate,
                                                            categoryId = selectedCategory.id,
                                                            description = description.ifBlank { null },
                                                            isShared = isShared,
                                                            recurringSeriesId = recurringSeriesId
                                                        )
                                                    )
                                                }
                                                repository.insertExpenses(
                                                    expenses = expenses
                                                )
                                            }.onSuccess {
                                                onClose()
                                            }.onFailure {
                                                snackbarHostState.showSnackbar("Unable to save expense")
                                            }
                                            isSaving = false
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                if (isSaving) "Saving..."
                                else if (expenseId == null) "Save Expense"
                                else "Update Expense"
                            )
                        }
                    }
                }
            }
        }
    }

    private fun buildExpenseId(): String {
        return "${Clock.System.now().toEpochMilliseconds()}-${Random.nextLong()}"
    }

    private fun buildRecurringSeriesId(): String {
        return "recurring-${buildExpenseId()}"
    }

    private fun Long.formatDateLabel(): String {
        val date = Instant.fromEpochMilliseconds(this)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
        return "${date.year}-${(date.month.ordinal + 1).toString().padStart(2, '0')}-${date.day.toString().padStart(2, '0')}"
    }
}
