package it.danielebufarini.spesify.ui.screens.income

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
import it.danielebufarini.spesify.data.CategoryManagementRepository
import it.danielebufarini.spesify.data.IncomeReadRepository
import it.danielebufarini.spesify.data.PendingIncome
import it.danielebufarini.spesify.data.TransactionWriteRepository
import it.danielebufarini.spesify.data.buildRecurringMonthlyIncomes
import it.danielebufarini.spesify.data.evaluateAmountExpressionInput
import it.danielebufarini.spesify.database.CATEGORY_TYPE_INCOME
import it.danielebufarini.spesify.localization.rememberCategoryNameResolver
import it.danielebufarini.spesify.ui.screens.expenses.clearActiveIosExpenseEditorSaveHandler
import it.danielebufarini.spesify.ui.screens.expenses.setActiveIosExpenseEditorSaveHandler
import it.danielebufarini.spesify.ui.screens.platform.rememberIsIosPlatform
import it.danielebufarini.spesify.ui.screens.platform.rememberPlatformDatePicker
import it.danielebufarini.spesify.ui.screens.transactions.rememberTransactionEditorFormState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import kotlin.time.Clock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddIncomeRoute(
    incomeId: String?,
    initialYear: Int?,
    initialMonth: Int?,
    showNavigationChrome: Boolean,
    onClose: () -> Unit,
    useHostedFloatingChrome: Boolean = false,
) {
    val categoryRepository: CategoryManagementRepository = koinInject()
    val incomeReadRepository: IncomeReadRepository = koinInject()
    val transactionWriteRepository: TransactionWriteRepository = koinInject()
    val labels = addIncomeRouteLabels()
    val isIos = rememberIsIosPlatform()
    val useIosHostedFloatingChrome = isIos && useHostedFloatingChrome
    val platformDatePicker = rememberPlatformDatePicker()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val resolveCategoryName = rememberCategoryNameResolver()
    val defaultDateMillis = remember(incomeId, initialYear, initialMonth) {
        if (incomeId == null) buildInitialIncomeDateMillis(initialYear, initialMonth) else null
    }
    val fallbackDateMillis = remember(incomeId) {
        Clock.System.now().toEpochMilliseconds()
    }

    val editorState = rememberTransactionEditorFormState(
        resetKey = incomeId,
        initialDateMillis = defaultDateMillis ?: fallbackDateMillis,
        initiallyInitialized = incomeId == null,
    )
    var pendingRecurringUpdate by remember { mutableStateOf<PendingRecurringIncomeUpdate?>(null) }
    var pendingRecurringAction by remember { mutableStateOf<RecurringIncomeAction?>(null) }

    val categoriesFlow = remember(categoryRepository) {
        categoryRepository.getAllCategories().flowOn(Dispatchers.Default)
    }
    val categories by categoriesFlow.collectAsState(initial = emptyList())
    val selectableCategories = remember(categories, editorState.selectedCategoryId) {
        categories.filter { category ->
            category.categoryType == CATEGORY_TYPE_INCOME &&
                (category.isArchived != 1L || category.id == editorState.selectedCategoryId)
        }
    }
    val selectedCategory = categories.find { it.id == editorState.selectedCategoryId }
    val evaluatedAmount = remember(editorState.amount) { evaluateAmountExpressionInput(editorState.amount) }
    val isAmountValid = evaluatedAmount != null && evaluatedAmount > 0L

    LaunchedEffect(incomeId) {
        if (incomeId == null || editorState.isInitialized) return@LaunchedEffect

        val income = withContext(Dispatchers.Default) {
            incomeReadRepository.getIncomeById(incomeId)
        } ?: return@LaunchedEffect
        editorState.initializeFromIncome(income)
    }

    fun dismissRecurringDialog() {
        pendingRecurringUpdate = null
        pendingRecurringAction = null
    }

    suspend fun saveIncomeUpdate(updateWholeSeries: Boolean, payload: PendingRecurringIncomeUpdate) {
        val result = withContext(Dispatchers.Default) {
            runCatching {
                val currentIncomeId = incomeId ?: return@runCatching
                val seriesId = editorState.recurringSeriesId
                if (updateWholeSeries && !seriesId.isNullOrBlank()) {
                    transactionWriteRepository.updateRecurringIncomeSeries(
                        anchorIncomeId = currentIncomeId,
                        seriesId = seriesId,
                        amount = payload.amount,
                        date = payload.date,
                        description = payload.description,
                        categoryId = payload.categoryId,
                    )
                } else {
                    transactionWriteRepository.insertIncomes(
                        incomes = listOf(
                            PendingIncome(
                                id = currentIncomeId,
                                amount = payload.amount,
                                date = payload.date,
                                description = payload.description,
                                categoryId = payload.categoryId,
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
            snackbarHostState.showSnackbar(labels.unableToSaveIncome)
        }
        editorState.isSaving = false
    }

    suspend fun deleteIncome(deleteWholeSeries: Boolean) {
        val result = withContext(Dispatchers.Default) {
            runCatching {
                val currentIncomeId = incomeId ?: return@runCatching
                val seriesId = editorState.recurringSeriesId
                if (deleteWholeSeries && !seriesId.isNullOrBlank()) {
                    transactionWriteRepository.deleteRecurringIncomeSeries(seriesId)
                } else {
                    transactionWriteRepository.deleteIncome(currentIncomeId)
                }
            }
        }
        result.onSuccess {
            dismissRecurringDialog()
            onClose()
        }.onFailure {
            snackbarHostState.showSnackbar(labels.unableToDeleteIncome)
        }
        editorState.isSaving = false
    }

    fun requestDeleteIncome() {
        if (editorState.recurringSeriesId != null) {
            pendingRecurringAction = RecurringIncomeAction.Delete
        } else {
            scope.launch {
                editorState.isSaving = true
                deleteIncome(deleteWholeSeries = false)
            }
        }
    }

    fun requestSaveIncome() {
        scope.launch {
            val parsedAmount = evaluateAmountExpressionInput(editorState.amount)
            if (parsedAmount == null || parsedAmount <= 0L) {
                snackbarHostState.showSnackbar(labels.enterValidAmount)
                return@launch
            }

            val normalizedDescription = editorState.description.trim().ifBlank { null }
            val normalizedCategoryId = editorState.selectedCategoryId?.takeIf { it.isNotBlank() }
            if (incomeId != null && editorState.recurringSeriesId != null) {
                pendingRecurringUpdate = PendingRecurringIncomeUpdate(
                    amount = parsedAmount,
                    date = editorState.selectedDateMillis ?: fallbackDateMillis,
                    description = normalizedDescription,
                    categoryId = normalizedCategoryId,
                )
                pendingRecurringAction = RecurringIncomeAction.Update
                return@launch
            }

            editorState.isSaving = true
            saveIncome(
                repository = transactionWriteRepository,
                incomeId = incomeId,
                amount = parsedAmount,
                date = editorState.selectedDateMillis ?: fallbackDateMillis,
                description = editorState.description,
                categoryId = normalizedCategoryId,
                isRecurringMonthly = editorState.isRecurringMonthly,
                onClose = onClose,
                snackbarHostState = snackbarHostState,
                unableToSaveIncomeLabel = labels.unableToSaveIncome,
            )
            editorState.isSaving = false
        }
    }

    SideEffect {
        if (useIosHostedFloatingChrome) {
            setActiveIosExpenseEditorSaveHandler(::requestSaveIncome)
        }
    }
    DisposableEffect(useIosHostedFloatingChrome) {
        onDispose {
            if (useIosHostedFloatingChrome) {
                clearActiveIosExpenseEditorSaveHandler()
            }
        }
    }

    AddIncomeEditorContent(
        labels = labels,
        snackbarHostState = snackbarHostState,
        showNavigationChrome = showNavigationChrome,
        isIos = isIos,
        useFloatingBottomBar = isIos,
        useIosHostedFloatingChrome = useIosHostedFloatingChrome,
        contentTopPadding = if (useIosHostedFloatingChrome) 220.dp else 16.dp,
        contentBottomPadding = if (useIosHostedFloatingChrome) 132.dp else 16.dp,
        isInitialized = editorState.isInitialized,
        incomeId = incomeId,
        isSaving = editorState.isSaving,
        amount = editorState.amount,
        isAmountValid = isAmountValid,
        onAmountChange = { editorState.amount = it },
        selectedCategoryName = selectedCategory?.let { resolveCategoryName(it.id, it.name) },
        selectedCategoryIconKey = selectedCategory?.icon,
        selectedCategoryId = editorState.selectedCategoryId,
        onSelectCategory = { editorState.showCategoryPickerSheet = true },
        selectedDateMillis = editorState.selectedDateMillis ?: fallbackDateMillis,
        onSelectDate = {
            platformDatePicker.show(editorState.selectedDateMillis ?: fallbackDateMillis) { pickedDate ->
                editorState.selectedDateMillis = pickedDate
            }
        },
        description = editorState.description,
        onDescriptionChange = { editorState.description = it },
        isRecurringMonthly = editorState.isRecurringMonthly,
        onRecurringMonthlyChange = { editorState.isRecurringMonthly = it },
        recurringSeriesId = editorState.recurringSeriesId,
        onClose = onClose,
        onSave = ::requestSaveIncome,
        onDelete = ::requestDeleteIncome,
    )

    AddIncomeSheetsAndDialogs(
        labels = labels,
        repository = categoryRepository,
        scopeLaunch = { block -> scope.launch { block() } },
        showAddCategorySheet = editorState.showAddCategorySheet,
        onAddCategorySheetChange = { editorState.showAddCategorySheet = it },
        showCategoryPickerSheet = editorState.showCategoryPickerSheet,
        onCategoryPickerSheetChange = { editorState.showCategoryPickerSheet = it },
        selectableCategories = selectableCategories,
        selectedCategoryId = editorState.selectedCategoryId,
        onCategorySelected = { editorState.selectedCategoryId = it },
        resolveCategoryName = { category -> resolveCategoryName(category.id, category.name) },
        pendingRecurringAction = pendingRecurringAction,
        pendingRecurringUpdate = pendingRecurringUpdate,
        onDismissRecurringDialog = {
            dismissRecurringDialog()
            editorState.isSaving = false
        },
        onSavingChange = { editorState.isSaving = it },
        saveIncomeUpdate = ::saveIncomeUpdate,
        deleteIncome = ::deleteIncome,
        snackbarHostState = snackbarHostState,
    )

    platformDatePicker.Render()
}

private suspend fun saveIncome(
    repository: TransactionWriteRepository,
    incomeId: String?,
    amount: Long,
    date: Long,
    description: String,
    categoryId: String?,
    isRecurringMonthly: Boolean,
    onClose: () -> Unit,
    snackbarHostState: SnackbarHostState,
    unableToSaveIncomeLabel: String,
) {
    val result = withContext(Dispatchers.Default) {
        runCatching {
            val incomes = if (incomeId == null && isRecurringMonthly) {
                buildRecurringMonthlyIncomes(
                    amount = amount,
                    firstDate = date,
                    description = description.trim(),
                    categoryId = categoryId,
                    recurringSeriesId = buildRecurringIncomeSeriesId(),
                    idProvider = ::buildIncomeId,
                )
            } else {
                listOf(
                    PendingIncome(
                        id = incomeId ?: buildIncomeId(),
                        amount = amount,
                        date = date,
                        description = description.trim().ifBlank { null },
                        categoryId = categoryId,
                        recurringSeriesId = null,
                    ),
                )
            }
            repository.insertIncomes(incomes = incomes)
        }
    }
    result.onSuccess {
        onClose()
    }.onFailure {
        snackbarHostState.showSnackbar(unableToSaveIncomeLabel)
    }
}
