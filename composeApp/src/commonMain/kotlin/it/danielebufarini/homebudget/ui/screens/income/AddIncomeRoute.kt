package it.danielebufarini.homebudget.ui.screens.income

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
import it.danielebufarini.homebudget.data.ExpenseRepository
import it.danielebufarini.homebudget.data.PendingIncome
import it.danielebufarini.homebudget.data.buildRecurringMonthlyIncomes
import it.danielebufarini.homebudget.data.formatAmountInput
import it.danielebufarini.homebudget.data.parseAmountInput
import it.danielebufarini.homebudget.database.CATEGORY_TYPE_INCOME
import it.danielebufarini.homebudget.localization.rememberCategoryNameResolver
import it.danielebufarini.homebudget.ui.screens.expenses.clearActiveIosExpenseEditorSaveHandler
import it.danielebufarini.homebudget.ui.screens.expenses.setActiveIosExpenseEditorSaveHandler
import it.danielebufarini.homebudget.ui.screens.platform.rememberIsIosPlatform
import it.danielebufarini.homebudget.ui.screens.platform.rememberPlatformDatePicker
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
    val repository: ExpenseRepository = koinInject()
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

    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var selectedDateMillis by remember(incomeId, defaultDateMillis) {
        mutableStateOf(defaultDateMillis ?: Clock.System.now().toEpochMilliseconds())
    }
    var isRecurringMonthly by remember { mutableStateOf(false) }
    var recurringSeriesId by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var isInitialized by remember(incomeId) { mutableStateOf(incomeId == null) }
    var showAddCategorySheet by remember { mutableStateOf(false) }
    var showCategoryPickerSheet by remember { mutableStateOf(false) }
    var pendingRecurringUpdate by remember { mutableStateOf<PendingRecurringIncomeUpdate?>(null) }
    var pendingRecurringAction by remember { mutableStateOf<RecurringIncomeAction?>(null) }

    val categoriesFlow = remember(repository) {
        repository.getAllCategories().flowOn(Dispatchers.Default)
    }
    val categories by categoriesFlow.collectAsState(initial = emptyList())
    val selectableCategories = remember(categories, selectedCategoryId) {
        categories.filter { category ->
            category.categoryType == CATEGORY_TYPE_INCOME &&
                (category.isArchived != 1L || category.id == selectedCategoryId)
        }
    }
    val selectedCategory = categories.find { it.id == selectedCategoryId }

    LaunchedEffect(incomeId) {
        if (incomeId == null || isInitialized) return@LaunchedEffect

        val income = withContext(Dispatchers.Default) {
            repository.getIncomeById(incomeId)
        } ?: return@LaunchedEffect
        amount = formatAmountInput(income.amount)
        description = income.description.orEmpty()
        selectedCategoryId = income.categoryId
        selectedDateMillis = income.date
        recurringSeriesId = income.recurringSeriesId
        isInitialized = true
    }

    fun dismissRecurringDialog() {
        pendingRecurringUpdate = null
        pendingRecurringAction = null
    }

    suspend fun saveIncomeUpdate(updateWholeSeries: Boolean, payload: PendingRecurringIncomeUpdate) {
        val result = withContext(Dispatchers.Default) {
            runCatching {
                val currentIncomeId = incomeId ?: return@runCatching
                val seriesId = recurringSeriesId
                if (updateWholeSeries && !seriesId.isNullOrBlank()) {
                    repository.updateRecurringIncomeSeries(
                        anchorIncomeId = currentIncomeId,
                        seriesId = seriesId,
                        amount = payload.amount,
                        date = payload.date,
                        description = payload.description,
                        categoryId = payload.categoryId,
                    )
                } else {
                    repository.insertIncomes(
                        incomes = listOf(
                            PendingIncome(
                                id = currentIncomeId,
                                amount = payload.amount,
                                date = payload.date,
                                description = payload.description,
                                categoryId = payload.categoryId,
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
            snackbarHostState.showSnackbar(labels.unableToSaveIncome)
        }
        isSaving = false
    }

    suspend fun deleteIncome(deleteWholeSeries: Boolean) {
        val result = withContext(Dispatchers.Default) {
            runCatching {
                val currentIncomeId = incomeId ?: return@runCatching
                val seriesId = recurringSeriesId
                if (deleteWholeSeries && !seriesId.isNullOrBlank()) {
                    repository.deleteRecurringIncomeSeries(seriesId)
                } else {
                    repository.deleteIncome(currentIncomeId)
                }
            }
        }
        result.onSuccess {
            dismissRecurringDialog()
            onClose()
        }.onFailure {
            snackbarHostState.showSnackbar(labels.unableToDeleteIncome)
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

    fun requestSaveIncome() {
        scope.launch {
            val parsedAmount = parseAmountInput(amount)
            if (parsedAmount == null || parsedAmount <= 0L) {
                snackbarHostState.showSnackbar(labels.enterValidAmount)
                return@launch
            }

            val normalizedDescription = description.trim().ifBlank { null }
            val normalizedCategoryId = selectedCategoryId?.takeIf { it.isNotBlank() }
            if (incomeId != null && recurringSeriesId != null) {
                pendingRecurringUpdate = PendingRecurringIncomeUpdate(
                    amount = parsedAmount,
                    date = selectedDateMillis,
                    description = normalizedDescription,
                    categoryId = normalizedCategoryId,
                )
                pendingRecurringAction = RecurringIncomeAction.Update
                return@launch
            }

            isSaving = true
            saveIncome(
                repository = repository,
                incomeId = incomeId,
                amount = parsedAmount,
                date = selectedDateMillis,
                description = description,
                categoryId = normalizedCategoryId,
                isRecurringMonthly = isRecurringMonthly,
                onClose = onClose,
                snackbarHostState = snackbarHostState,
                unableToSaveIncomeLabel = labels.unableToSaveIncome,
            )
            isSaving = false
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
        isInitialized = isInitialized,
        incomeId = incomeId,
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
        isRecurringMonthly = isRecurringMonthly,
        onRecurringMonthlyChange = { isRecurringMonthly = it },
        recurringSeriesId = recurringSeriesId,
        onClose = onClose,
        onSave = ::requestSaveIncome,
        onDelete = ::requestDeleteIncome,
    )

    AddIncomeSheetsAndDialogs(
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
        saveIncomeUpdate = ::saveIncomeUpdate,
        deleteIncome = ::deleteIncome,
        snackbarHostState = snackbarHostState,
    )

    platformDatePicker.Render()
}

private suspend fun saveIncome(
    repository: ExpenseRepository,
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
