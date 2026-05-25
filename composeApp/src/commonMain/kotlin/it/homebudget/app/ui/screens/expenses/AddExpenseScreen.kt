package it.homebudget.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.add
import homebudget.composeapp.generated.resources.add_category
import homebudget.composeapp.generated.resources.add_expense
import homebudget.composeapp.generated.resources.amount
import homebudget.composeapp.generated.resources.back
import homebudget.composeapp.generated.resources.cancel
import homebudget.composeapp.generated.resources.category
import homebudget.composeapp.generated.resources.close
import homebudget.composeapp.generated.resources.date
import homebudget.composeapp.generated.resources.delete_expense
import homebudget.composeapp.generated.resources.delete_recurring_expense_title
import homebudget.composeapp.generated.resources.description
import homebudget.composeapp.generated.resources.edit_expense
import homebudget.composeapp.generated.resources.enter_valid_amount
import homebudget.composeapp.generated.resources.expense_details
import homebudget.composeapp.generated.resources.installments
import homebudget.composeapp.generated.resources.options
import homebudget.composeapp.generated.resources.recurring_expense_action_delete
import homebudget.composeapp.generated.resources.recurring_expense_action_update
import homebudget.composeapp.generated.resources.recurring_expense_info
import homebudget.composeapp.generated.resources.recurring_expense_series_info
import homebudget.composeapp.generated.resources.recurring_monthly
import homebudget.composeapp.generated.resources.save_expense
import homebudget.composeapp.generated.resources.saving
import homebudget.composeapp.generated.resources.select_category
import homebudget.composeapp.generated.resources.select_date
import homebudget.composeapp.generated.resources.select_installments
import homebudget.composeapp.generated.resources.shared_expense
import homebudget.composeapp.generated.resources.single_payment
import homebudget.composeapp.generated.resources.unable_to_delete_expense
import homebudget.composeapp.generated.resources.unable_to_save_expense
import homebudget.composeapp.generated.resources.update_expense
import homebudget.composeapp.generated.resources.update_recurring_expense_title
import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.data.IdGenerator
import it.homebudget.app.data.PendingExpense
import it.homebudget.app.data.RECURRING_MONTHLY_OCCURRENCES
import it.homebudget.app.data.buildPendingExpenses
import it.homebudget.app.data.buildRecurringMonthlyExpenses
import it.homebudget.app.data.buildRecurringMonthlyExpensesFromExistingExpense
import it.homebudget.app.data.formatAmountInput
import it.homebudget.app.data.parseAmountInput
import it.homebudget.app.database.CATEGORY_TYPE_EXPENSE
import it.homebudget.app.database.Category
import it.homebudget.app.localization.rememberCategoryNameResolver
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.time.Clock
import kotlin.time.Instant

private data class PendingRecurringExpenseUpdate(
    val amount: Long,
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
        onClose: () -> Unit,
        useHostedFloatingChrome: Boolean = false
    ) {
        val repository: ExpenseRepository = koinInject()
        val isIos = rememberIsIosPlatform()
        val useIosTransactionFloatingChrome = isIos && useHostedFloatingChrome
        val useIosHostedFloatingChrome =
            useIosTransactionFloatingChrome || (isIos && (expenseId != null || readOnly))
        val useAndroidFixedActionChrome = !isIos && (expenseId != null || readOnly)
        val contentTopPadding = if (useIosTransactionFloatingChrome) 220.dp else 16.dp
        val contentBottomPadding = if (useIosTransactionFloatingChrome) 132.dp else 16.dp
        val platformDatePicker = rememberPlatformDatePicker()
        val platformOptionPicker = rememberPlatformOptionPicker()
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }
        val addExpenseLabel = stringResource(Res.string.add_expense)
        val addCategoryLabel = stringResource(Res.string.add_category)
        val addLabel = stringResource(Res.string.add)
        val amountLabel = stringResource(Res.string.amount)
        val backLabel = stringResource(Res.string.back)
        val cancelLabel = stringResource(Res.string.cancel)
        val categoryLabel = stringResource(Res.string.category)
        val closeLabel = stringResource(Res.string.close)
        val dateLabel = stringResource(Res.string.date)
        val deleteExpenseLabel = stringResource(Res.string.delete_expense)
        val deleteRecurringExpenseTitle = stringResource(Res.string.delete_recurring_expense_title)
        val descriptionLabel = stringResource(Res.string.description)
        val editExpenseLabel = stringResource(Res.string.edit_expense)
        val enterValidAmountLabel = stringResource(Res.string.enter_valid_amount)
        val expenseDetailsLabel = stringResource(Res.string.expense_details)
        val installmentsLabel = stringResource(Res.string.installments)
        val recurringExpenseActionDelete = stringResource(Res.string.recurring_expense_action_delete)
        val recurringExpenseActionUpdate = stringResource(Res.string.recurring_expense_action_update)
        val recurringExpenseInfo = stringResource(
            Res.string.recurring_expense_info,
            RECURRING_MONTHLY_OCCURRENCES / 12
        )
        val recurringExpenseSeriesInfo = stringResource(Res.string.recurring_expense_series_info)
        val recurringMonthlyLabel = stringResource(Res.string.recurring_monthly)
        val saveExpenseLabel = stringResource(Res.string.save_expense)
        val savingLabel = stringResource(Res.string.saving)
        val selectCategoryLabel = stringResource(Res.string.select_category)
        val selectDateLabel = stringResource(Res.string.select_date)
        val selectInstallmentsLabel = stringResource(Res.string.select_installments)
        val sharedExpenseLabel = stringResource(Res.string.shared_expense)
        val singlePaymentLabel = stringResource(Res.string.single_payment)
        val unableToDeleteExpenseLabel = stringResource(Res.string.unable_to_delete_expense)
        val unableToSaveExpenseLabel = stringResource(Res.string.unable_to_save_expense)
        val updateExpenseLabel = stringResource(Res.string.update_expense)
        val updateRecurringExpenseTitle = stringResource(Res.string.update_recurring_expense_title)
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

        val categories by repository.getAllCategories().collectAsState(initial = emptyList())
        val selectableCategories = remember(categories, selectedCategoryId) {
            categories.filter { category ->
                category.categoryType == CATEGORY_TYPE_EXPENSE &&
                    (category.isArchived != 1L || category.id == selectedCategoryId)
            }
        }
        val selectedCategory = categories.find { it.id == selectedCategoryId }
        val optionsLabel = stringResource(Res.string.options)
        val installmentOptions = remember { (1..12).toList() }
        val installmentLabels = remember(installmentOptions, singlePaymentLabel, installmentsLabel) {
            installmentOptions.associateWith { count ->
                if (count == 1) {
                    singlePaymentLabel
                } else {
                    "$count ${installmentsLabel.lowercase()}"
                }
            }
        }
        val selectedCategoryName = selectedCategory?.let {
            resolveCategoryName(it.id, it.name)
        }
        val selectedCategoryIconKey = selectedCategory?.icon

        EnsureStarterCategoriesSeeded(repository)

        LaunchedEffect(expenseId, categories) {
            if (expenseId == null || isInitialized) {
                return@LaunchedEffect
            }

            val expense = repository.getExpenseById(expenseId) ?: return@LaunchedEffect
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
                snackbarHostState.showSnackbar(unableToSaveExpenseLabel)
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
                snackbarHostState.showSnackbar(unableToDeleteExpenseLabel)
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
                    parsedAmount == null || parsedAmount <= 0L -> {
                        snackbarHostState.showSnackbar(enterValidAmountLabel)
                    }
                    selectedCategoryId.isBlank() -> {
                        snackbarHostState.showSnackbar(selectCategoryLabel)
                    }
                    expenseDate == null -> {
                        snackbarHostState.showSnackbar(selectDateLabel)
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
                                } else if (isRecurringMonthly) {
                                    buildRecurringMonthlyExpensesFromExistingExpense(
                                        existingExpenseId = expenseId,
                                        amount = parsedAmount,
                                        firstDate = expenseDate,
                                        categoryId = selectedCategoryId,
                                        description = description,
                                        isShared = isShared,
                                        recurringSeriesId = buildRecurringSeriesId(),
                                        idProvider = ::buildExpenseId
                                    )
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
                                snackbarHostState.showSnackbar(unableToSaveExpenseLabel)
                            }
                            isSaving = false
                        }
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
        val screenTitle = when {
            readOnly -> expenseDetailsLabel
            expenseId == null -> addExpenseLabel
            else -> editExpenseLabel
        }

        Scaffold(
            containerColor = if (useIosHostedFloatingChrome) {
                Color.Transparent
            } else {
                MaterialTheme.colorScheme.background
            },
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            },
            topBar = {
                if (showNavigationChrome) {
                    TopAppBar(
                        title = {
                            Text(screenTitle)
                        },
                        navigationIcon = {
                            if (isIos) {
                                TextButton(onClick = onClose) {
                                    Text(backLabel)
                                }
                            }
                        },
                        actions = {
                            if (useAndroidFixedActionChrome && !readOnly && expenseId != null) {
                                IconButton(
                                    onClick = ::requestDeleteExpense,
                                    enabled = !isSaving
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = deleteExpenseLabel
                                    )
                                }
                            }
                        }
                    )
                }
            },
            bottomBar = {
                if (useAndroidFixedActionChrome && isInitialized) {
                    if (readOnly) {
                        SoftSecondaryButton(
                            text = closeLabel,
                            onClick = onClose,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .navigationBarsPadding()
                        )
                    } else {
                        SoftActionBar(
                            cancelLabel = cancelLabel,
                            confirmLabel = if (isSaving) {
                                savingLabel
                            } else if (expenseId == null) {
                                saveExpenseLabel
                            } else {
                                updateExpenseLabel
                            },
                            confirmEnabled = !isSaving,
                            onCancel = onClose,
                            onConfirm = ::requestSaveExpense,
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .navigationBarsPadding()
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(
                        start = 16.dp,
                        top = contentTopPadding,
                        end = 16.dp,
                        bottom = contentBottomPadding
                    )
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                if (!isInitialized) {
                    TransactionEditorSkeleton()
                } else {
                    TransactionAmountHeader(
                        value = amount,
                        onValueChange = { if (!readOnly) amount = it },
                        label = amountLabel,
                        kind = TransactionEditorKind.Expense,
                        readOnly = readOnly
                    )

                    SoftSectionCard(title = expenseDetailsLabel) {
                        SoftCategoryPickerRow(
                            label = categoryLabel,
                            categoryName = selectedCategoryName,
                            categoryIconKey = selectedCategoryIconKey,
                            categoryColorKey = selectedCategoryId,
                            placeholder = selectCategoryLabel,
                            enabled = !readOnly,
                            onClick = { showCategoryPickerSheet = true }
                        )

                        SoftPickerRow(
                            label = dateLabel,
                            value = selectedDateMillis?.formatDateLabel().orEmpty(),
                            icon = DateIcon,
                            enabled = !readOnly,
                            onClick = {
                                platformDatePicker.show(selectedDateMillis) { pickedDate ->
                                    selectedDateMillis = pickedDate
                                }
                            }
                        )

                        SoftTextField(
                            value = description,
                            onValueChange = { if (!readOnly) description = it },
                            label = descriptionLabel,
                            leadingIcon = DescriptionIcon,
                            readOnly = readOnly,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }

                    SoftSectionCard(title = optionsLabel) {
                        if (expenseId == null && !isRecurringMonthly) {
                            SoftPickerRow(
                                label = installmentsLabel,
                                value = installmentLabels.getValue(installmentCount),
                                icon = InstallmentsIcon,
                                enabled = !readOnly,
                                onClick = {
                                    val options = installmentOptions.map { installmentLabels.getValue(it) }
                                    platformOptionPicker.show(
                                        title = selectInstallmentsLabel,
                                        options = options,
                                        selectedOption = installmentLabels.getValue(installmentCount)
                                    ) { selectedOption ->
                                        installmentCount = installmentOptions.first { option ->
                                            installmentLabels.getValue(option) == selectedOption
                                        }
                                    }
                                }
                            )
                        }

                        if (!readOnly && recurringSeriesId == null) {
                            SoftToggleRow(
                                label = recurringMonthlyLabel,
                                description = null,
                                icon = RecurringIcon,
                                checked = isRecurringMonthly,
                                onCheckedChange = {
                                    isRecurringMonthly = it
                                    if (it) {
                                        installmentCount = 1
                                    }
                                }
                            )
                            SoftInlineInfoCard(
                                visible = isRecurringMonthly,
                                text = recurringExpenseInfo,
                                icon = RecurringIcon
                            )
                        }

                        if (recurringSeriesId != null) {
                            RecurringSeriesNotice(
                                text = recurringExpenseSeriesInfo
                            )
                        }

                        SoftToggleRow(
                            label = sharedExpenseLabel,
                            description = null,
                            icon = SharedIcon,
                            checked = isShared,
                            onCheckedChange = { if (!readOnly) isShared = it },
                            enabled = !readOnly
                        )
                    }

                    if (readOnly && !useIosHostedFloatingChrome && !useAndroidFixedActionChrome) {
                        SoftSecondaryButton(
                            text = closeLabel,
                            onClick = onClose,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else if (!readOnly && !useIosHostedFloatingChrome && !useAndroidFixedActionChrome) {
                        SoftActionBar(
                            cancelLabel = cancelLabel,
                            confirmLabel = if (isSaving) savingLabel else if (expenseId == null) saveExpenseLabel else updateExpenseLabel,
                            confirmEnabled = !isSaving,
                            onCancel = onClose,
                            onConfirm = ::requestSaveExpense
                        )
                    }
                }
            }
        }

        if (showAddCategorySheet) {
            AddCategorySheet(
                onDismiss = {
                    showAddCategorySheet = false
                },
                title = addCategoryLabel,
                confirmLabel = addLabel,
                initialName = "",
                initialIconKey = DEFAULT_CATEGORY_ICON_KEY,
                onConfirm = { name, iconKey ->
                    scope.launch {
                        runCatching {
                            val categoryId = buildCategoryId()
                            repository.insertCategory(
                                id = categoryId,
                                name = name,
                                icon = iconKey,
                                categoryType = CATEGORY_TYPE_EXPENSE
                            )
                            selectedCategoryId = categoryId
                        }.onSuccess {
                            showAddCategorySheet = false
                        }.onFailure {
                            snackbarHostState.showSnackbar(unableToSaveExpenseLabel)
                        }
                    }
                }
            )
        }

        if (showCategoryPickerSheet) {
            CategoryPickerSheet(
                categories = selectableCategories,
                selectedCategoryId = selectedCategoryId,
                resolveCategoryName = { category ->
                    resolveCategoryName(category.id, category.name)
                },
                onDismiss = { showCategoryPickerSheet = false },
                onAddCategory = {
                    showCategoryPickerSheet = false
                    showAddCategorySheet = true
                },
                onCategorySelected = { categoryId ->
                    selectedCategoryId = categoryId
                    showCategoryPickerSheet = false
                }
            )
        }

        if (pendingRecurringAction != null) {
            RecurringSeriesActionDialog(
                title = when (pendingRecurringAction) {
                    RecurringExpenseAction.Update -> updateRecurringExpenseTitle
                    RecurringExpenseAction.Delete -> deleteRecurringExpenseTitle
                    null -> ""
                },
                message = when (pendingRecurringAction) {
                    RecurringExpenseAction.Update -> recurringExpenseActionUpdate
                    RecurringExpenseAction.Delete -> recurringExpenseActionDelete
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

        platformDatePicker.Render()
    }

    private fun buildExpenseId(): String = IdGenerator.newId("expense")

    private fun buildRecurringSeriesId(): String = IdGenerator.newId("recurring-expense")

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
    categoryIconKey: String?,
    categoryColorKey: String?,
    enabled: Boolean,
    canSelectCategory: Boolean,
    onSelectCategory: () -> Unit,
    onAddCategory: () -> Unit
) {
    val categoryLabel = stringResource(Res.string.category)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = categoryLabel,
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
                categoryIconKey = categoryIconKey,
                categoryColorKey = categoryColorKey,
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
    categoryIconKey: String?,
    categoryColorKey: String?,
    enabled: Boolean,
    canSelectCategory: Boolean,
    onSelectCategory: () -> Unit,
    onAddCategory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val addCategoryLabel = stringResource(Res.string.add_category)
    val selectCategoryLabel = stringResource(Res.string.select_category)

    SplitButtonLayout(
        modifier = modifier,
        leadingButton = {
            SplitButtonDefaults.LeadingButton(
                onClick = onSelectCategory,
                enabled = enabled && canSelectCategory,
                colors = homeBudgetButtonColors()
            ) {
                CategoryLabel(
                    iconKey = categoryIconKey ?: DEFAULT_CATEGORY_ICON_KEY,
                    text = categoryName ?: selectCategoryLabel,
                    colorKey = categoryColorKey,
                    textStyle = MaterialTheme.typography.labelLarge,
                    textColor = MaterialTheme.colorScheme.onPrimary,
                    iconTint = MaterialTheme.colorScheme.onPrimary,
                    iconSize = 16.dp,
                    maxLines = 1
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
                    contentDescription = addCategoryLabel
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoryPickerSheet(
    categories: List<Category>,
    selectedCategoryId: String,
    resolveCategoryName: (Category) -> String,
    onDismiss: () -> Unit,
    onAddCategory: () -> Unit,
    onCategorySelected: (String) -> Unit
) {
    val selectCategoryLabel = stringResource(Res.string.select_category)
    val addCategoryLabel = stringResource(Res.string.add_category)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = selectCategoryLabel,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            CategoryGridSection(
                title = selectCategoryLabel,
                categories = categories,
                selectedCategoryId = selectedCategoryId,
                resolveCategoryName = resolveCategoryName,
                showAddTile = true,
                addCategoryLabel = addCategoryLabel,
                onAddCategory = onAddCategory,
                onCategorySelected = { category -> onCategorySelected(category.id) }
            )
        }
    }
}

@Composable
private fun CategoryGridSection(
    title: String,
    categories: List<Category>,
    selectedCategoryId: String,
    resolveCategoryName: (Category) -> String,
    showAddTile: Boolean,
    addCategoryLabel: String,
    onAddCategory: () -> Unit,
    onCategorySelected: (Category) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        CategoryGrid(
            categories = categories,
            selectedCategoryId = selectedCategoryId,
            resolveCategoryName = resolveCategoryName,
            showAddTile = showAddTile,
            addCategoryLabel = addCategoryLabel,
            onAddCategory = onAddCategory,
            onCategorySelected = onCategorySelected
        )
    }
}

@Composable
private fun CategoryGrid(
    categories: List<Category>,
    selectedCategoryId: String,
    resolveCategoryName: (Category) -> String,
    showAddTile: Boolean,
    addCategoryLabel: String,
    onAddCategory: () -> Unit,
    onCategorySelected: (Category) -> Unit
) {
    val columns = 4
    val entries = if (showAddTile) listOf<Category?>(null) + categories else categories

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        entries.chunked(columns).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowItems.forEach { category ->
                    if (category == null) {
                        AddCategoryGridTile(
                            label = addCategoryLabel,
                            onClick = onAddCategory,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        CategoryGridTile(
                            category = category,
                            categoryName = resolveCategoryName(category),
                            isSelected = category.id == selectedCategoryId,
                            onClick = { onCategorySelected(category) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                repeat(columns - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AddCategoryGridTile(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(24.dp)
    val tileBackgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val tileBorderColor = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = modifier
            .aspectRatio(0.90f)
            .clip(shape)
            .background(tileBackgroundColor, shape)
            .border(1.dp, tileBorderColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = label,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    lineHeight = 11.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CategoryGridTile(
    category: Category,
    categoryName: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(24.dp)
    val tileBackgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val tileContentColor = MaterialTheme.colorScheme.onSurface
    val tileBorderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val tileBorderWidth = if (isSelected) 2.dp else 1.dp

    Box(
        modifier = modifier
            .aspectRatio(0.90f)
            .clip(shape)
            .background(tileBackgroundColor, shape)
            .border(tileBorderWidth, tileBorderColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CategoryIcon(
                iconKey = category.icon,
                colorKey = category.id,
                modifier = Modifier.size(24.dp),
                tint = null
            )
            Text(
                text = categoryName,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 10.sp,
                    lineHeight = 11.sp
                ),
                color = tileContentColor,
                maxLines = 2,
                textAlign = TextAlign.Center
            )
        }

        if (isSelected) {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd),
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(4.dp)
                        .size(14.dp)
                )
            }
        }
    }
}
