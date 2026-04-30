package it.homebudget.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import com.ionspin.kotlin.bignum.integer.BigInteger
import it.homebudget.app.data.*
import it.homebudget.app.localization.LocalStrings
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
        val strings = LocalStrings.current

        var amount by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var selectedCategoryId by remember { mutableStateOf("") }
        var selectedDateMillis by remember { mutableStateOf<Long?>(Clock.System.now().toEpochMilliseconds()) }
        var installmentCount by remember { mutableStateOf(1) }
        var isRecurringMonthly by remember { mutableStateOf(false) }
        var recurringSeriesId by remember { mutableStateOf<String?>(null) }
        var isShared by remember { mutableStateOf(false) }
        var installmentExpanded by remember { mutableStateOf(false) }
        var isSaving by remember { mutableStateOf(false) }
        var showAddCategorySheet by remember { mutableStateOf(false) }
        var isInitialized by remember(expenseId) { mutableStateOf(expenseId == null) }
        var pendingRecurringUpdate by remember { mutableStateOf<PendingRecurringExpenseUpdate?>(null) }
        var pendingRecurringAction by remember { mutableStateOf<RecurringExpenseAction?>(null) }

        val categories by repository.getAllCategories().collectAsState(initial = emptyList())
        val selectedCategory = categories.find { it.id == selectedCategoryId }
        val installmentOptions = remember { (1..12).toList() }
        val installmentLabels = remember(installmentOptions, strings) {
            installmentOptions.associateWith(strings::installmentLabel)
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
            selectedCategoryId = expense.categoryId
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
                snackbarHostState.showSnackbar(strings.unableToSaveExpense)
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
                snackbarHostState.showSnackbar(strings.unableToDeleteExpense)
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
                                    readOnly -> strings.expenseDetails
                                    expenseId == null -> strings.addExpense
                                    else -> strings.editExpense
                                }
                            )
                        },
                        navigationIcon = {
                            if (isIos) {
                                TextButton(onClick = onClose) {
                                    Text(strings.back)
                                }
                            }
                        }
                    )
                }
            },
            floatingActionButton = {
                if (!isIos && !readOnly && expenseId != null) {
                    DeleteEditItemFab(
                        label = strings.deleteExpense,
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
                    label = strings.amount,
                    readOnly = readOnly,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                PlatformTextField(
                    value = description,
                    onValueChange = { if (!readOnly) description = it },
                    label = strings.description,
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
                        label = strings.date,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                CategorySelectorRow(
                    categoryName = selectedCategory?.let { strings.categoryName(it.id, it.name, it.isCustom) },
                    enabled = !readOnly,
                    canSelectCategory = categories.isNotEmpty(),
                    onSelectCategory = {
                        platformOptionPicker.show(
                            title = strings.selectCategory,
                            options = categories.map { strings.categoryName(it.id, it.name, it.isCustom) },
                            selectedOption = selectedCategory?.let {
                                strings.categoryName(it.id, it.name, it.isCustom)
                            }
                        ) { selectedOption ->
                            selectedCategoryId = categories
                                .firstOrNull {
                                    strings.categoryName(it.id, it.name, it.isCustom) == selectedOption
                                }
                                ?.id
                                .orEmpty()
                        }
                    },
                    onAddCategory = { showAddCategorySheet = true }
                )

                if (expenseId == null && !isRecurringMonthly) {
                    if (isIos) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !readOnly) {
                                    val options = installmentOptions.map { installmentLabels.getValue(it) }
                                    platformOptionPicker.show(
                                        title = strings.selectInstallments,
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
                                label = strings.installments,
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
                                label = strings.installments,
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
                            Text(strings.recurringMonthly)
                        }
                        if (isRecurringMonthly) {
                            Text(
                                text = strings.recurringExpenseInfo(RECURRING_MONTHLY_OCCURRENCES / 12),
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                if (recurringSeriesId != null) {
                    RecurringSeriesNotice(
                        text = strings.recurringExpenseSeriesInfo()
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
                    Text(strings.sharedExpense)
                }

                if (readOnly) {
                    TextButton(
                        onClick = onClose,
                        colors = homeBudgetTextButtonColors(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(strings.close)
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
                            Text(strings.cancel)
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
                                            snackbarHostState.showSnackbar(strings.enterValidAmount)
                                        }
                                        selectedCategoryId.isBlank() -> {
                                            snackbarHostState.showSnackbar(strings.selectCategory)
                                        }
                                        expenseDate == null -> {
                                            snackbarHostState.showSnackbar(strings.selectDate)
                                        }
                                        else -> {
                                            isSaving = true
                                            val normalizedDescription = description.ifBlank { null }
                                            if (expenseId != null && recurringSeriesId != null) {
                                                pendingRecurringUpdate = PendingRecurringExpenseUpdate(
                                                    amount = parsedAmount,
                                                    date = expenseDate,
                                                    categoryId = selectedCategoryId,
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
                                                                categoryId = selectedCategoryId,
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
                                                                categoryId = selectedCategoryId,
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
                                                                categoryId = selectedCategoryId,
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
                                                    snackbarHostState.showSnackbar(strings.unableToSaveExpense)
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
                                if (isSaving) strings.saving
                                else if (expenseId == null) strings.saveExpense
                                else strings.updateExpense
                            )
                        }
                    }
                }
            }
        }

        if (showAddCategorySheet) {
            AddCategorySheet(
                onDismiss = { showAddCategorySheet = false },
                onAddCategory = { name ->
                    scope.launch {
                        val categoryId = buildCategoryId()
                        runCatching {
                            repository.insertCategory(
                                id = categoryId,
                                name = name,
                                icon = "category",
                                isCustom = true
                            )
                        }.onSuccess {
                            selectedCategoryId = categoryId
                            showAddCategorySheet = false
                        }.onFailure {
                            snackbarHostState.showSnackbar(strings.unableToSaveExpense)
                        }
                    }
                }
            )
        }

        if (pendingRecurringAction != null) {
            RecurringSeriesActionDialog(
                title = when (pendingRecurringAction) {
                    RecurringExpenseAction.Update -> strings.updateRecurringExpenseTitle
                    RecurringExpenseAction.Delete -> strings.deleteRecurringExpenseTitle
                    null -> ""
                },
                message = when (pendingRecurringAction) {
                    RecurringExpenseAction.Update -> strings.recurringExpenseActionMessage(isUpdate = true)
                    RecurringExpenseAction.Delete -> strings.recurringExpenseActionMessage(isUpdate = false)
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

    private fun buildCategoryId(): String {
        return "custom_${Clock.System.now().toEpochMilliseconds()}_${Random.nextLong()}"
    }

    private fun Long.formatDateLabel(): String {
        val date = Instant.fromEpochMilliseconds(this)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
        return "${date.year}-${(date.month.ordinal + 1).toString().padStart(2, '0')}-${date.day.toString().padStart(2, '0')}"
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CategorySelectorRow(
    categoryName: String?,
    enabled: Boolean,
    canSelectCategory: Boolean,
    onSelectCategory: () -> Unit,
    onAddCategory: () -> Unit
) {
    val strings = LocalStrings.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = strings.category,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(end = 16.dp)
        )
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterEnd
        ) {
            CategorySplitButton(
                categoryName = categoryName,
                enabled = enabled,
                canSelectCategory = canSelectCategory,
                onSelectCategory = onSelectCategory,
                onAddCategory = onAddCategory,
                modifier = Modifier.widthIn(max = 240.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CategorySplitButton(
    categoryName: String?,
    enabled: Boolean,
    canSelectCategory: Boolean,
    onSelectCategory: () -> Unit,
    onAddCategory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current

    SplitButtonLayout(
        modifier = modifier,
        leadingButton = {
            SplitButtonDefaults.LeadingButton(
                onClick = onSelectCategory,
                enabled = enabled && canSelectCategory,
                colors = homeBudgetButtonColors()
            ) {
                Text(
                    text = categoryName ?: strings.selectCategory,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        trailingButton = {
            SplitButtonDefaults.TrailingButton(
                onClick = onAddCategory,
                enabled = enabled,
                colors = homeBudgetButtonColors()
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    modifier = Modifier.size(SplitButtonDefaults.TrailingIconSize),
                    contentDescription = strings.addCategory
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCategorySheet(
    onDismiss: () -> Unit,
    onAddCategory: (String) -> Unit
) {
    val strings = LocalStrings.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var categoryName by remember { mutableStateOf("") }
    val trimmedCategoryName = categoryName.trim()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = strings.addCategory,
                style = MaterialTheme.typography.titleLarge
            )
            PlatformTextField(
                value = categoryName,
                onValueChange = { categoryName = it },
                label = strings.categoryName,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onDismiss,
                    colors = homeBudgetTextButtonColors()
                ) {
                    Text(strings.cancel)
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = trimmedCategoryName.isNotEmpty(),
                    onClick = { onAddCategory(trimmedCategoryName) },
                    colors = homeBudgetButtonColors()
                ) {
                    Text(strings.add)
                }
            }
        }
    }
}
