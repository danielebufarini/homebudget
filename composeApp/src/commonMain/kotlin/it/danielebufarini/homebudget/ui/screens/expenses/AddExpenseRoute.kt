package it.danielebufarini.homebudget.ui.screens.expenses

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
import it.danielebufarini.homebudget.data.CategoryManagementRepository
import it.danielebufarini.homebudget.data.ExpenseReadRepository
import it.danielebufarini.homebudget.data.PendingExpense
import it.danielebufarini.homebudget.data.TransactionWriteRepository
import it.danielebufarini.homebudget.data.buildPendingExpenses
import it.danielebufarini.homebudget.data.buildRecurringMonthlyExpenses
import it.danielebufarini.homebudget.data.buildRecurringMonthlyExpensesFromExistingExpense
import it.danielebufarini.homebudget.data.evaluateAmountExpressionInput
import it.danielebufarini.homebudget.database.CATEGORY_TYPE_EXPENSE
import it.danielebufarini.homebudget.localization.rememberCategoryNameResolver
import it.danielebufarini.homebudget.ui.screens.categories.EnsureStarterCategoriesSeeded
import it.danielebufarini.homebudget.ui.screens.platform.rememberIsIosPlatform
import it.danielebufarini.homebudget.ui.screens.platform.rememberPlatformDatePicker
import it.danielebufarini.homebudget.ui.screens.transactions.rememberTransactionEditorFormState
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
    val categoryRepository: CategoryManagementRepository = koinInject()
    val expenseReadRepository: ExpenseReadRepository = koinInject()
    val transactionWriteRepository: TransactionWriteRepository = koinInject()
    val labels = addExpenseRouteLabels()
    val isIos = rememberIsIosPlatform()
    val useIosTransactionFloatingChrome = isIos && useHostedFloatingChrome
    val useIosHostedFloatingChrome = useIosTransactionFloatingChrome || (isIos && (expenseId != null || readOnly))
    val useAndroidFixedActionChrome = !isIos && (expenseId != null || readOnly)
    val platformDatePicker = rememberPlatformDatePicker()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val resolveCategoryName = rememberCategoryNameResolver()
    val initialDateMillis = remember(expenseId) {
        Clock.System.now().toEpochMilliseconds()
    }

    val editorState = rememberTransactionEditorFormState(
        resetKey = expenseId,
        initialDateMillis = initialDateMillis,
        initiallyInitialized = expenseId == null,
    )
    var pendingRecurringUpdate by remember { mutableStateOf<PendingRecurringExpenseUpdate?>(null) }
    var pendingRecurringAction by remember { mutableStateOf<RecurringExpenseAction?>(null) }

    val categoriesFlow = remember(categoryRepository) {
        categoryRepository.getAllCategories().flowOn(Dispatchers.Default)
    }
    val categories by categoriesFlow.collectAsState(initial = emptyList())
    val selectedCategoryId = editorState.selectedCategoryId.orEmpty()
    val selectableCategories = remember(categories, selectedCategoryId) {
        categories.filter { category ->
            category.categoryType == CATEGORY_TYPE_EXPENSE &&
                (category.isArchived != 1L || category.id == selectedCategoryId)
        }
    }
    val selectedCategory = categories.find { it.id == selectedCategoryId }
    val evaluatedAmount = remember(editorState.amount) { evaluateAmountExpressionInput(editorState.amount) }
    val isAmountValid = evaluatedAmount != null && evaluatedAmount > 0L

    EnsureStarterCategoriesSeeded(categoryRepository)

    LaunchedEffect(expenseId, categories) {
        if (expenseId == null || editorState.isInitialized) return@LaunchedEffect

        val expense = withContext(Dispatchers.Default) {
            expenseReadRepository.getExpenseById(expenseId)
        } ?: return@LaunchedEffect
        editorState.initializeFromExpense(expense)
    }

    fun dismissRecurringDialog() {
        pendingRecurringUpdate = null
        pendingRecurringAction = null
    }

    suspend fun saveExpenseUpdate(updateWholeSeries: Boolean, payload: PendingRecurringExpenseUpdate) {
        val result = withContext(Dispatchers.Default) {
            runCatching {
                val currentExpenseId = expenseId ?: return@runCatching
                val seriesId = editorState.recurringSeriesId
                if (updateWholeSeries && !seriesId.isNullOrBlank()) {
                    transactionWriteRepository.updateRecurringExpenseSeries(
                        anchorExpenseId = currentExpenseId,
                        seriesId = seriesId,
                        amount = payload.amount,
                        date = payload.date,
                        categoryId = payload.categoryId,
                        description = payload.description,
                        isShared = payload.isShared,
                    )
                } else {
                    transactionWriteRepository.insertExpenses(
                        expenses = listOf(
                            PendingExpense(
                                id = currentExpenseId,
                                amount = payload.amount,
                                date = payload.date,
                                categoryId = payload.categoryId,
                                description = payload.description,
                                isShared = payload.isShared,
                                recurringSeriesId = editorState.recurringSeriesId,
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
        editorState.isSaving = false
    }

    suspend fun deleteExpense(deleteWholeSeries: Boolean) {
        val result = withContext(Dispatchers.Default) {
            runCatching {
                val currentExpenseId = expenseId ?: return@runCatching
                val seriesId = editorState.recurringSeriesId
                if (deleteWholeSeries && !seriesId.isNullOrBlank()) {
                    transactionWriteRepository.deleteRecurringExpenseSeries(seriesId)
                } else {
                    transactionWriteRepository.deleteExpense(currentExpenseId)
                }
            }
        }
        result.onSuccess {
            dismissRecurringDialog()
            onClose()
        }.onFailure {
            snackbarHostState.showSnackbar(labels.unableToDeleteExpense)
        }
        editorState.isSaving = false
    }

    fun requestDeleteExpense() {
        if (editorState.recurringSeriesId != null) {
            pendingRecurringAction = RecurringExpenseAction.Delete
        } else {
            scope.launch {
                editorState.isSaving = true
                deleteExpense(deleteWholeSeries = false)
            }
        }
    }

    fun requestSaveExpense() {
        scope.launch {
            val parsedAmount = evaluateAmountExpressionInput(editorState.amount)
            val expenseDate = editorState.selectedDateMillis

            when {
                parsedAmount == null || parsedAmount <= 0L -> snackbarHostState.showSnackbar(labels.enterValidAmount)
                selectedCategoryId.isBlank() -> snackbarHostState.showSnackbar(labels.selectCategory)
                expenseDate == null -> snackbarHostState.showSnackbar(labels.selectDate)
                expenseId != null && editorState.recurringSeriesId != null -> {
                    pendingRecurringUpdate = PendingRecurringExpenseUpdate(
                        amount = parsedAmount,
                        date = expenseDate,
                        categoryId = selectedCategoryId,
                        description = editorState.description.ifBlank { null },
                        isShared = editorState.isShared,
                    )
                    pendingRecurringAction = RecurringExpenseAction.Update
                }
                else -> {
                    editorState.isSaving = true
                    saveExpense(
                        repository = transactionWriteRepository,
                        expenseId = expenseId,
                        amount = parsedAmount,
                        date = expenseDate,
                        categoryId = selectedCategoryId,
                        description = editorState.description,
                        isShared = editorState.isShared,
                        isRecurringMonthly = editorState.isRecurringMonthly,
                        installmentCount = editorState.installmentCount,
                        recurringSeriesId = editorState.recurringSeriesId,
                        onClose = onClose,
                        snackbarHostState = snackbarHostState,
                        unableToSaveExpenseLabel = labels.unableToSaveExpense,
                    )
                    editorState.isSaving = false
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
        isInitialized = editorState.isInitialized,
        readOnly = readOnly,
        expenseId = expenseId,
        isSaving = editorState.isSaving,
        amount = editorState.amount,
        isAmountValid = isAmountValid,
        onAmountChange = { editorState.amount = it },
        selectedCategoryName = selectedCategory?.let { resolveCategoryName(it.id, it.name) },
        selectedCategoryIconKey = selectedCategory?.icon,
        selectedCategoryId = selectedCategoryId,
        onSelectCategory = { editorState.showCategoryPickerSheet = true },
        selectedDateMillis = editorState.selectedDateMillis,
        onSelectDate = {
            platformDatePicker.show(editorState.selectedDateMillis) { pickedDate ->
                editorState.selectedDateMillis = pickedDate
            }
        },
        description = editorState.description,
        onDescriptionChange = { editorState.description = it },
        installmentCount = editorState.installmentCount,
        onInstallmentCountChange = editorState::updateInstallmentCount,
        isRecurringMonthly = editorState.isRecurringMonthly,
        onRecurringMonthlyChange = editorState::updateExpenseRecurringMonthly,
        recurringSeriesId = editorState.recurringSeriesId,
        isShared = editorState.isShared,
        onSharedChange = { editorState.isShared = it },
        onClose = onClose,
        onSave = ::requestSaveExpense,
        onDelete = ::requestDeleteExpense,
    )

    AddExpenseSheetsAndDialogs(
        labels = labels,
        repository = categoryRepository,
        scopeLaunch = { block -> scope.launch { block() } },
        showAddCategorySheet = editorState.showAddCategorySheet,
        onAddCategorySheetChange = { editorState.showAddCategorySheet = it },
        showCategoryPickerSheet = editorState.showCategoryPickerSheet,
        onCategoryPickerSheetChange = { editorState.showCategoryPickerSheet = it },
        selectableCategories = selectableCategories,
        selectedCategoryId = selectedCategoryId,
        onCategorySelected = { editorState.selectedCategoryId = it },
        resolveCategoryName = { category -> resolveCategoryName(category.id, category.name) },
        pendingRecurringAction = pendingRecurringAction,
        pendingRecurringUpdate = pendingRecurringUpdate,
        onDismissRecurringDialog = {
            dismissRecurringDialog()
            editorState.isSaving = false
        },
        onSavingChange = { editorState.isSaving = it },
        saveExpenseUpdate = ::saveExpenseUpdate,
        deleteExpense = ::deleteExpense,
        snackbarHostState = snackbarHostState,
    )

    platformDatePicker.Render()
}

private suspend fun saveExpense(
    repository: TransactionWriteRepository,
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
