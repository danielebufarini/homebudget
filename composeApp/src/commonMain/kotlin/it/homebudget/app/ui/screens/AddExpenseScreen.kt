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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Instant

private data class PendingRecurringExpenseUpdate(
    val amount: BigInteger,
    val date: Long,
    val categoryId: String,
    val description: String?,
    val isShared: Boolean
)

private enum class RecurringExpenseAction {
    Update,
    Delete
}

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
        var categoryExpanded by remember { mutableStateOf(false) }
        var installmentExpanded by remember { mutableStateOf(false) }
        var isSaving by remember { mutableStateOf(false) }
        var isInitialized by remember(expenseId) { mutableStateOf(expenseId == null) }
        var pendingRecurringUpdate by remember { mutableStateOf<PendingRecurringExpenseUpdate?>(null) }
        var pendingRecurringAction by remember { mutableStateOf<RecurringExpenseAction?>(null) }

        val categories by repository.getAllCategories().collectAsState(initial = emptyList())
        val selectedCategory = categories.find { it.name == category }
        val installmentOptions = remember { (1..12).toList() }
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

        suspend fun saveExpenseUpdate(
            updateWholeSeries: Boolean,
            payload: PendingRecurringExpenseUpdate
        ) {
            runCatching {
                val currentExpenseId = expenseId ?: return@runCatching
                val seriesId = recurringSeriesId
                if (updateWholeSeries && !seriesId.isNullOrBlank()) {
                    repository.updateRecurringExpenseSeries(
                        anchorExpenseId = currentExpenseId,
                        seriesId = seriesId,
                        amount = payload.amount,
                        date = payload.date,
                        categoryId = payload.categoryId,
                        description = payload.description,
                        isShared = payload.isShared
                    )
                } else {
                    repository.insertExpenses(
                        expenses = listOf(
                            PendingExpense(
                                id = currentExpenseId,
                                amount = payload.amount,
                                date = payload.date,
                                categoryId = payload.categoryId,
                                description = payload.description,
                                isShared = payload.isShared,
                                recurringSeriesId = recurringSeriesId
                            )
                        )
                    )
                }
            }.onSuccess {
                closeAfterRecurringAction()
            }.onFailure {
                snackbarHostState.showSnackbar("Unable to save expense")
            }
            isSaving = false
        }

        suspend fun deleteExpense(deleteWholeSeries: Boolean) {
            runCatching {
                val currentExpenseId = expenseId ?: return@runCatching
                val seriesId = recurringSeriesId
                if (deleteWholeSeries && !seriesId.isNullOrBlank()) {
                    repository.deleteRecurringExpenseSeries(seriesId)
                } else {
                    repository.deleteExpense(currentExpenseId)
                }
            }.onSuccess {
                closeAfterRecurringAction()
            }.onFailure {
                snackbarHostState.showSnackbar("Unable to delete expense")
            }
            isSaving = false
        }

        fun requestDeleteExpense() {
            if (recurringSeriesId != null) {
                pendingRecurringAction = RecurringExpenseAction.Delete
            } else {
                scope.launch {
                    isSaving = true
                    deleteExpense(deleteWholeSeries = false)
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
            },
            floatingActionButton = {
                if (!isIos && !readOnly && expenseId != null) {
                    DeleteEditItemFab(
                        label = "Delete expense",
                        enabled = !isSaving,
                        onClick = ::requestDeleteExpense
                    )
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .padding(bottom = if (!isIos && !readOnly && expenseId != null) 88.dp else 0.dp)
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
                        colors = homeBudgetTextButtonColors(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Close")
                    }
                } else {
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
                                            val normalizedDescription = description.ifBlank { null }
                                            if (expenseId != null && recurringSeriesId != null) {
                                                pendingRecurringUpdate = PendingRecurringExpenseUpdate(
                                                    amount = parsedAmount,
                                                    date = expenseDate,
                                                    categoryId = selectedCategory.id,
                                                    description = normalizedDescription,
                                                    isShared = isShared
                                                )
                                                pendingRecurringAction = RecurringExpenseAction.Update
                                                isSaving = false
                                            } else {
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
                                                        listOf(
                                                            PendingExpense(
                                                                id = expenseId,
                                                                amount = parsedAmount,
                                                                date = expenseDate,
                                                                categoryId = selectedCategory.id,
                                                                description = normalizedDescription,
                                                                isShared = isShared,
                                                                recurringSeriesId = recurringSeriesId
                                                            )
                                                        )
                                                    }
                                                    repository.insertExpenses(expenses = expenses)
                                                }.onSuccess {
                                                    onClose()
                                                }.onFailure {
                                                    snackbarHostState.showSnackbar("Unable to save expense")
                                                }
                                                isSaving = false
                                            }
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

        if (pendingRecurringAction != null) {
            RecurringSeriesActionDialog(
                title = when (pendingRecurringAction) {
                    RecurringExpenseAction.Update -> "Update recurring expense?"
                    RecurringExpenseAction.Delete -> "Delete recurring expense?"
                    null -> ""
                },
                message = when (pendingRecurringAction) {
                    RecurringExpenseAction.Update -> "Do you want to update only this expense or the whole recurring series?"
                    RecurringExpenseAction.Delete -> "Do you want to delete only this expense or the whole recurring series?"
                    null -> ""
                },
                onThisInstanceOnly = {
                    scope.launch {
                        isSaving = true
                        when (pendingRecurringAction) {
                            RecurringExpenseAction.Update -> {
                                val payload = pendingRecurringUpdate
                                if (payload == null) {
                                    isSaving = false
                                } else {
                                    saveExpenseUpdate(
                                        updateWholeSeries = false,
                                        payload = payload
                                    )
                                }
                            }
                            RecurringExpenseAction.Delete -> {
                                deleteExpense(deleteWholeSeries = false)
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
                            RecurringExpenseAction.Update -> {
                                val payload = pendingRecurringUpdate
                                if (payload == null) {
                                    isSaving = false
                                } else {
                                    saveExpenseUpdate(
                                        updateWholeSeries = true,
                                        payload = payload
                                    )
                                }
                            }
                            RecurringExpenseAction.Delete -> {
                                deleteExpense(deleteWholeSeries = true)
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
