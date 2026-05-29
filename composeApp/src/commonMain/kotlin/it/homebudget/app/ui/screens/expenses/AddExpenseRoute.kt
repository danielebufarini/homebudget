package it.homebudget.app.ui.screens

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.data.MAX_EXPENSE_INSTALLMENTS
import it.homebudget.app.data.PendingExpense
import it.homebudget.app.data.buildPendingExpenses
import it.homebudget.app.data.buildRecurringMonthlyExpenses
import it.homebudget.app.data.buildRecurringMonthlyExpensesFromExistingExpense
import it.homebudget.app.data.formatAmountInput
import it.homebudget.app.data.parseAmountInput
import it.homebudget.app.database.CATEGORY_TYPE_EXPENSE
import it.homebudget.app.localization.rememberCategoryNameResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import kotlin.time.Clock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddExpenseRoute(
    expenseId: String?,
    readOnly: Boolean,
    showNavigationChrome: Boolean,
    onClose: () -> Unit,
    useHostedFloatingChrome: Boolean = false,
) {
    val repository: ExpenseRepository = koinInject()
    val labels = addExpenseRouteLabels()
    val isIos = rememberIsIosPlatform()
    val useIosTransactionFloatingChrome = isIos && useHostedFloatingChrome
    val useIosHostedFloatingChrome = useIosTransactionFloatingChrome || (isIos && (expenseId != null || readOnly))
    val useAndroidFixedActionChrome = !isIos && (expenseId != null || readOnly)
    val platformDatePicker = rememberPlatformDatePicker()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val resolveCategoryName = rememberCategoryNameResolver()

    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf("") }
    var selectedDateMillis by remember { mutableStateOf<Long?>(Clock.System.now().toEpochMilliseconds()) }
    var installmentCount by remember { mutableStateOf(1) }
    var isRecurringMonthly by remember { mutableStateOf(false) }
    var recurringSeriesId by remember { mutableStateOf<String?>(null) }
    var isShared by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var showAddCategorySheet by remember { mutableStateOf(false) }
    var showCategoryPickerSheet by remember { mutableStateOf(false) }
    var isInitialized by remember(expenseId) { mutableStateOf(expenseId == null) }
    var pendingRecurringUpdate by remember { mutableStateOf<PendingRecurringExpenseUpdate?>(null) }
    var pendingRecurringAction by remember { mutableStateOf<RecurringExpenseAction?>(null) }

    val categoriesFlow = remember(repository) {
        repository.getAllCategories().flowOn(Dispatchers.Default)
    }
    val categories by categoriesFlow.collectAsState(initial = emptyList())
    val selectableCategories = remember(categories, selectedCategoryId) {
        categories.filter { category ->
            category.categoryType == CATEGORY_TYPE_EXPENSE &&
                (category.isArchived != 1L || category.id == selectedCategoryId)
        }
    }
    val selectedCategory = categories.find { it.id == selectedCategoryId }

    EnsureStarterCategoriesSeeded(repository)

    LaunchedEffect(expenseId, categories) {
        if (expenseId == null || isInitialized) return@LaunchedEffect

        val expense = withContext(Dispatchers.Default) {
            repository.getExpenseById(expenseId)
        } ?: return@LaunchedEffect
        amount = formatAmountInput(expense.amount)
        description = expense.description.orEmpty()
        selectedCategoryId = expense.categoryId
        selectedDateMillis = expense.date
        isRecurringMonthly = expense.recurringSeriesId != null
        recurringSeriesId = expense.recurringSeriesId
        isShared = expense.isShared == 1L
        isInitialized = true
    }

    fun dismissRecurringDialog() {
        pendingRecurringUpdate = null
        pendingRecurringAction = null
    }

    suspend fun saveExpenseUpdate(updateWholeSeries: Boolean, payload: PendingRecurringExpenseUpdate) {
        val result = withContext(Dispatchers.Default) {
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
                        isShared = payload.isShared,
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
                                recurringSeriesId = recurringSeriesId,
                            ),
                        ),
                    )
                }
            }
        }
        result.onSuccess {
            dismissRecurringDialog()
            onClose()
        }.onFailure {
            snackbarHostState.showSnackbar(labels.unableToSaveExpense)
        }
        isSaving = false
    }

    suspend fun deleteExpense(deleteWholeSeries: Boolean) {
        val result = withContext(Dispatchers.Default) {
            runCatching {
                val currentExpenseId = expenseId ?: return@runCatching
                val seriesId = recurringSeriesId
                if (deleteWholeSeries && !seriesId.isNullOrBlank()) {
                    repository.deleteRecurringExpenseSeries(seriesId)
                } else {
                    repository.deleteExpense(currentExpenseId)
                }
            }
        }
        result.onSuccess {
            dismissRecurringDialog()
            onClose()
        }.onFailure {
            snackbarHostState.showSnackbar(labels.unableToDeleteExpense)
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

    fun requestSaveExpense() {
        scope.launch {
            val parsedAmount = parseAmountInput(amount)
            val expenseDate = selectedDateMillis

            when {
                parsedAmount == null || parsedAmount <= 0L -> snackbarHostState.showSnackbar(labels.enterValidAmount)
                selectedCategoryId.isBlank() -> snackbarHostState.showSnackbar(labels.selectCategory)
                expenseDate == null -> snackbarHostState.showSnackbar(labels.selectDate)
                expenseId != null && recurringSeriesId != null -> {
                    pendingRecurringUpdate = PendingRecurringExpenseUpdate(
                        amount = parsedAmount,
                        date = expenseDate,
                        categoryId = selectedCategoryId,
                        description = description.ifBlank { null },
                        isShared = isShared,
                    )
                    pendingRecurringAction = RecurringExpenseAction.Update
                }
                else -> {
                    isSaving = true
                    saveExpense(
                        repository = repository,
                        expenseId = expenseId,
                        amount = parsedAmount,
                        date = expenseDate,
                        categoryId = selectedCategoryId,
                        description = description,
                        isShared = isShared,
                        isRecurringMonthly = isRecurringMonthly,
                        installmentCount = installmentCount,
                        recurringSeriesId = recurringSeriesId,
                        onClose = onClose,
                        snackbarHostState = snackbarHostState,
                        unableToSaveExpenseLabel = labels.unableToSaveExpense,
                    )
                    isSaving = false
                }
            }
        }
    }

    SideEffect {
        if (useIosHostedFloatingChrome && !readOnly) {
            setActiveIosExpenseEditorSaveHandler(::requestSaveExpense)
        }
    }
    DisposableEffect(useIosHostedFloatingChrome, readOnly) {
        onDispose {
            if (useIosHostedFloatingChrome && !readOnly) {
                clearActiveIosExpenseEditorSaveHandler()
            }
        }
    }

    AddExpenseEditorContent(
        labels = labels,
        snackbarHostState = snackbarHostState,
        screenTitle = when {
            readOnly -> labels.expenseDetails
            expenseId == null -> labels.addExpense
            else -> labels.editExpense
        },
        showNavigationChrome = showNavigationChrome,
        isIos = isIos,
        useIosHostedFloatingChrome = useIosHostedFloatingChrome,
        useAndroidFixedActionChrome = useAndroidFixedActionChrome,
        contentTopPadding = if (useIosTransactionFloatingChrome) 220.dp else 16.dp,
        contentBottomPadding = if (useIosTransactionFloatingChrome) 132.dp else 16.dp,
        isInitialized = isInitialized,
        readOnly = readOnly,
        expenseId = expenseId,
        isSaving = isSaving,
        amount = amount,
        onAmountChange = { amount = it },
        selectedCategoryName = selectedCategory?.let { resolveCategoryName(it.id, it.name) },
        selectedCategoryIconKey = selectedCategory?.icon,
        selectedCategoryId = selectedCategoryId,
        onSelectCategory = { showCategoryPickerSheet = true },
        selectedDateMillis = selectedDateMillis,
        onSelectDate = {
            platformDatePicker.show(selectedDateMillis) { pickedDate ->
                selectedDateMillis = pickedDate
            }
        },
        description = description,
        onDescriptionChange = { description = it },
        installmentCount = installmentCount,
        onInstallmentCountChange = {
            val normalizedInstallmentCount = it.coerceIn(1, MAX_EXPENSE_INSTALLMENTS)
            installmentCount = normalizedInstallmentCount
            if (normalizedInstallmentCount > 1) {
                isRecurringMonthly = false
            }
        },
        isRecurringMonthly = isRecurringMonthly,
        onRecurringMonthlyChange = {
            isRecurringMonthly = it
            if (it) installmentCount = 1
        },
        recurringSeriesId = recurringSeriesId,
        isShared = isShared,
        onSharedChange = { isShared = it },
        onClose = onClose,
        onSave = ::requestSaveExpense,
        onDelete = ::requestDeleteExpense,
    )

    AddExpenseSheetsAndDialogs(
        labels = labels,
        repository = repository,
        scopeLaunch = { block -> scope.launch { block() } },
        showAddCategorySheet = showAddCategorySheet,
        onAddCategorySheetChange = { showAddCategorySheet = it },
        showCategoryPickerSheet = showCategoryPickerSheet,
        onCategoryPickerSheetChange = { showCategoryPickerSheet = it },
        selectableCategories = selectableCategories,
        selectedCategoryId = selectedCategoryId,
        onCategorySelected = { selectedCategoryId = it },
        resolveCategoryName = { category -> resolveCategoryName(category.id, category.name) },
        pendingRecurringAction = pendingRecurringAction,
        pendingRecurringUpdate = pendingRecurringUpdate,
        onDismissRecurringDialog = {
            dismissRecurringDialog()
            isSaving = false
        },
        onSavingChange = { isSaving = it },
        saveExpenseUpdate = ::saveExpenseUpdate,
        deleteExpense = ::deleteExpense,
        snackbarHostState = snackbarHostState,
    )

    platformDatePicker.Render()
}

private suspend fun saveExpense(
    repository: ExpenseRepository,
    expenseId: String?,
    amount: Long,
    date: Long,
    categoryId: String,
    description: String,
    isShared: Boolean,
    isRecurringMonthly: Boolean,
    installmentCount: Int,
    recurringSeriesId: String?,
    onClose: () -> Unit,
    snackbarHostState: SnackbarHostState,
    unableToSaveExpenseLabel: String,
) {
    val normalizedDescription = description.ifBlank { null }
    val result = withContext(Dispatchers.Default) {
        runCatching {
            val expenses = when {
                expenseId == null && isRecurringMonthly -> buildRecurringMonthlyExpenses(
                    amount = amount,
                    firstDate = date,
                    categoryId = categoryId,
                    description = description,
                    isShared = isShared,
                    recurringSeriesId = buildRecurringExpenseSeriesId(),
                    idProvider = ::buildExpenseId,
                )
                expenseId == null -> buildPendingExpenses(
                    amount = amount,
                    firstDate = date,
                    installments = installmentCount,
                    categoryId = categoryId,
                    description = description,
                    isShared = isShared,
                    idProvider = ::buildExpenseId,
                )
                isRecurringMonthly -> buildRecurringMonthlyExpensesFromExistingExpense(
                    existingExpenseId = expenseId,
                    amount = amount,
                    firstDate = date,
                    categoryId = categoryId,
                    description = description,
                    isShared = isShared,
                    recurringSeriesId = buildRecurringExpenseSeriesId(),
                    idProvider = ::buildExpenseId,
                )
                else -> listOf(
                    PendingExpense(
                        id = expenseId,
                        amount = amount,
                        date = date,
                        categoryId = categoryId,
                        description = normalizedDescription,
                        isShared = isShared,
                        recurringSeriesId = recurringSeriesId,
                    ),
                )
            }
            repository.insertExpenses(expenses = expenses)
        }
    }
    result.onSuccess {
        onClose()
    }.onFailure {
        snackbarHostState.showSnackbar(unableToSaveExpenseLabel)
    }
}
