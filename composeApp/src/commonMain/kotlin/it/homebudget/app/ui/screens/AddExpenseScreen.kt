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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.data.formatAmountInput
import it.homebudget.app.data.parseAmountInput
import it.homebudget.app.getPlatform
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
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val repository: ExpenseRepository = koinInject()
        val isIos = remember { getPlatform().isIos }
        val platformDatePicker = rememberPlatformDatePicker()
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }

        var amount by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("") }
        var selectedDateMillis by remember { mutableStateOf<Long?>(Clock.System.now().toEpochMilliseconds()) }
        var isShared by remember { mutableStateOf(false) }
        var expanded by remember { mutableStateOf(false) }
        var isSaving by remember { mutableStateOf(false) }
        var isInitialized by remember(expenseId) { mutableStateOf(expenseId == null) }

        val categories by repository.getAllCategories().collectAsState(initial = emptyList())
        val selectedCategory = categories.find { it.name == category }

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
            isShared = expense.isShared == 1L
            isInitialized = true
        }

        Scaffold(
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            },
            topBar = {
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
                            TextButton(onClick = { navigator?.pop() }) {
                                Text("back")
                            }
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { if (!readOnly) amount = it },
                    label = { Text("Amount") },
                    readOnly = readOnly,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { if (!readOnly) description = it },
                    label = { Text("Description") },
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
                    OutlinedTextField(
                        value = selectedDateMillis?.formatDateLabel().orEmpty(),
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text("Date") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                ExposedDropdownMenuBox(
                    expanded = expanded && !readOnly,
                    onExpandedChange = {
                        if (!readOnly) {
                            expanded = !expanded
                        }
                    }
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = {
                            if (!readOnly) {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                            }
                        },
                        enabled = !readOnly,
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded && !readOnly,
                        onDismissRequest = { expanded = false }
                    ) {
                        categories.forEach { selectionOption ->
                            DropdownMenuItem(
                                text = { Text(selectionOption.name) },
                                onClick = {
                                    category = selectionOption.name
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isShared,
                        onCheckedChange = { if (!readOnly) isShared = it },
                        enabled = !readOnly
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Shared Expense")
                }

                if (readOnly) {
                    TextButton(
                        onClick = { navigator?.pop() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Close")
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextButton(
                            onClick = { navigator?.pop() },
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
                                                repository.insertExpense(
                                                    id = expenseId ?: buildExpenseId(),
                                                    amount = parsedAmount,
                                                    date = expenseDate,
                                                    categoryId = selectedCategory.id,
                                                    description = description.ifBlank { null },
                                                    isShared = isShared
                                                )
                                            }.onSuccess {
                                                navigator?.pop()
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

    private fun Long.formatDateLabel(): String {
        val date = Instant.fromEpochMilliseconds(this)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
        return "${date.year}-${(date.month.ordinal + 1).toString().padStart(2, '0')}-${date.day.toString().padStart(2, '0')}"
    }
}
