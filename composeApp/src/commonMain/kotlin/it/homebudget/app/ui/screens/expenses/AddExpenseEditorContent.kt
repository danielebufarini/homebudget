package it.homebudget.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import it.homebudget.app.data.MAX_EXPENSE_INSTALLMENTS

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddExpenseEditorContent(
    labels: AddExpenseRouteLabels,
    snackbarHostState: SnackbarHostState,
    screenTitle: String,
    showNavigationChrome: Boolean,
    isIos: Boolean,
    useIosHostedFloatingChrome: Boolean,
    useAndroidFixedActionChrome: Boolean,
    contentTopPadding: Dp,
    contentBottomPadding: Dp,
    isInitialized: Boolean,
    readOnly: Boolean,
    expenseId: String?,
    isSaving: Boolean,
    amount: String,
    onAmountChange: (String) -> Unit,
    selectedCategoryName: String?,
    selectedCategoryIconKey: String?,
    selectedCategoryId: String,
    onSelectCategory: () -> Unit,
    selectedDateMillis: Long?,
    onSelectDate: () -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    installmentCount: Int,
    onInstallmentCountChange: (Int) -> Unit,
    isRecurringMonthly: Boolean,
    onRecurringMonthlyChange: (Boolean) -> Unit,
    recurringSeriesId: String?,
    isShared: Boolean,
    onSharedChange: (Boolean) -> Unit,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    Scaffold(
        containerColor = if (useIosHostedFloatingChrome) Color.Transparent else MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            if (showNavigationChrome) {
                AddExpenseTopBar(
                    title = screenTitle,
                    isIos = isIos,
                    labels = labels,
                    showDelete = useAndroidFixedActionChrome && !readOnly && expenseId != null,
                    deleteEnabled = !isSaving,
                    onClose = onClose,
                    onDelete = onDelete,
                )
            }
        },
        bottomBar = {
            AddExpenseBottomBar(
                visible = useAndroidFixedActionChrome && isInitialized,
                labels = labels,
                readOnly = readOnly,
                isSaving = isSaving,
                expenseId = expenseId,
                onClose = onClose,
                onSave = onSave,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(
                    start = 16.dp,
                    top = contentTopPadding,
                    end = 16.dp,
                    bottom = contentBottomPadding,
                )
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (!isInitialized) {
                TransactionEditorSkeleton()
            } else {
                AddExpenseForm(
                    labels = labels,
                    readOnly = readOnly,
                    expenseId = expenseId,
                    useIosHostedFloatingChrome = useIosHostedFloatingChrome,
                    useAndroidFixedActionChrome = useAndroidFixedActionChrome,
                    isSaving = isSaving,
                    amount = amount,
                    onAmountChange = onAmountChange,
                    selectedCategoryName = selectedCategoryName,
                    selectedCategoryIconKey = selectedCategoryIconKey,
                    selectedCategoryId = selectedCategoryId,
                    onSelectCategory = onSelectCategory,
                    selectedDateMillis = selectedDateMillis,
                    onSelectDate = onSelectDate,
                    description = description,
                    onDescriptionChange = onDescriptionChange,
                    installmentCount = installmentCount,
                    onInstallmentCountChange = onInstallmentCountChange,
                    isRecurringMonthly = isRecurringMonthly,
                    onRecurringMonthlyChange = onRecurringMonthlyChange,
                    recurringSeriesId = recurringSeriesId,
                    isShared = isShared,
                    onSharedChange = onSharedChange,
                    onClose = onClose,
                    onSave = onSave,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExpenseTopBar(
    title: String,
    isIos: Boolean,
    labels: AddExpenseRouteLabels,
    showDelete: Boolean,
    deleteEnabled: Boolean,
    onClose: () -> Unit,
    onDelete: () -> Unit,
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            if (isIos) {
                TextButton(onClick = onClose) {
                    Text(labels.back)
                }
            }
        },
        actions = {
            if (showDelete) {
                IconButton(onClick = onDelete, enabled = deleteEnabled) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = labels.deleteExpense,
                    )
                }
            }
        },
    )
}

@Composable
private fun AddExpenseBottomBar(
    visible: Boolean,
    labels: AddExpenseRouteLabels,
    readOnly: Boolean,
    isSaving: Boolean,
    expenseId: String?,
    onClose: () -> Unit,
    onSave: () -> Unit,
) {
    if (!visible) return

    if (readOnly) {
        SoftSecondaryButton(
            text = labels.close,
            onClick = onClose,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding(),
        )
    } else {
        SoftActionBar(
            cancelLabel = labels.cancel,
            confirmLabel = addExpenseConfirmLabel(labels, isSaving, expenseId),
            confirmEnabled = !isSaving,
            onCancel = onClose,
            onConfirm = onSave,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding(),
        )
    }
}

@Composable
private fun AddExpenseForm(
    labels: AddExpenseRouteLabels,
    readOnly: Boolean,
    expenseId: String?,
    useIosHostedFloatingChrome: Boolean,
    useAndroidFixedActionChrome: Boolean,
    isSaving: Boolean,
    amount: String,
    onAmountChange: (String) -> Unit,
    selectedCategoryName: String?,
    selectedCategoryIconKey: String?,
    selectedCategoryId: String,
    onSelectCategory: () -> Unit,
    selectedDateMillis: Long?,
    onSelectDate: () -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    installmentCount: Int,
    onInstallmentCountChange: (Int) -> Unit,
    isRecurringMonthly: Boolean,
    onRecurringMonthlyChange: (Boolean) -> Unit,
    recurringSeriesId: String?,
    isShared: Boolean,
    onSharedChange: (Boolean) -> Unit,
    onClose: () -> Unit,
    onSave: () -> Unit,
) {
    TransactionAmountHeader(
        value = amount,
        onValueChange = { if (!readOnly) onAmountChange(it) },
        label = labels.amount,
        kind = TransactionEditorKind.Expense,
        readOnly = readOnly,
    )

    SoftSectionCard(title = labels.expenseDetails) {
        SoftCategoryPickerRow(
            label = labels.category,
            categoryName = selectedCategoryName,
            categoryIconKey = selectedCategoryIconKey,
            categoryColorKey = selectedCategoryId,
            placeholder = labels.selectCategory,
            enabled = !readOnly,
            onClick = onSelectCategory,
        )
        SoftPickerRow(
            label = labels.date,
            value = selectedDateMillis?.formatExpenseDateLabel().orEmpty(),
            icon = DateIcon,
            enabled = !readOnly,
            onClick = onSelectDate,
        )
        SoftTextField(
            value = description,
            onValueChange = { if (!readOnly) onDescriptionChange(it) },
            label = labels.description,
            leadingIcon = DescriptionIcon,
            readOnly = readOnly,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }

    AddExpenseOptionsSection(
        labels = labels,
        readOnly = readOnly,
        expenseId = expenseId,
        installmentCount = installmentCount,
        onInstallmentCountChange = onInstallmentCountChange,
        isRecurringMonthly = isRecurringMonthly,
        onRecurringMonthlyChange = onRecurringMonthlyChange,
        recurringSeriesId = recurringSeriesId,
        isShared = isShared,
        onSharedChange = onSharedChange,
    )

    if (readOnly && !useIosHostedFloatingChrome && !useAndroidFixedActionChrome) {
        SoftSecondaryButton(text = labels.close, onClick = onClose, modifier = Modifier.fillMaxWidth())
    } else if (!readOnly && !useIosHostedFloatingChrome && !useAndroidFixedActionChrome) {
        SoftActionBar(
            cancelLabel = labels.cancel,
            confirmLabel = addExpenseConfirmLabel(labels, isSaving, expenseId),
            confirmEnabled = !isSaving,
            onCancel = onClose,
            onConfirm = onSave,
        )
    }
}

@Composable
private fun AddExpenseOptionsSection(
    labels: AddExpenseRouteLabels,
    readOnly: Boolean,
    expenseId: String?,
    installmentCount: Int,
    onInstallmentCountChange: (Int) -> Unit,
    isRecurringMonthly: Boolean,
    onRecurringMonthlyChange: (Boolean) -> Unit,
    recurringSeriesId: String?,
    isShared: Boolean,
    onSharedChange: (Boolean) -> Unit,
) {
    SoftSectionCard(title = labels.options) {
        if (expenseId == null && !isRecurringMonthly) {
            InstallmentRulerPicker(
                label = labels.installments,
                value = installmentCount,
                onValueChange = onInstallmentCountChange,
                enabled = !readOnly,
                valueRange = 1..MAX_EXPENSE_INSTALLMENTS,
                singlePaymentLabel = labels.singlePayment,
                installmentsLabel = labels.installments,
                icon = InstallmentsIcon,
            )
        }

        val showRecurringToggle = !readOnly && recurringSeriesId == null && installmentCount == 1
        AnimatedVisibility(
            visible = showRecurringToggle,
            enter = fadeIn(animationSpec = tween(180)) +
                expandVertically(animationSpec = tween(220)) +
                slideInVertically(animationSpec = tween(220)) { -it / 4 },
            exit = fadeOut(animationSpec = tween(120)) +
                shrinkVertically(animationSpec = tween(180)) +
                slideOutVertically(animationSpec = tween(180)) { -it / 4 },
        ) {
            Column {
                SoftToggleRow(
                    label = labels.recurringMonthly,
                    description = null,
                    icon = RecurringIcon,
                    checked = isRecurringMonthly,
                    onCheckedChange = onRecurringMonthlyChange,
                )
                SoftInlineInfoCard(
                    visible = isRecurringMonthly,
                    text = labels.recurringExpenseInfo,
                    icon = RecurringIcon,
                )
            }
        }

        if (recurringSeriesId != null) {
            RecurringSeriesNotice(text = labels.recurringExpenseSeriesInfo)
        }

        SoftToggleRow(
            label = labels.sharedExpense,
            description = null,
            icon = SharedIcon,
            checked = isShared,
            onCheckedChange = { if (!readOnly) onSharedChange(it) },
            enabled = !readOnly,
        )
    }
}

private fun addExpenseConfirmLabel(
    labels: AddExpenseRouteLabels,
    isSaving: Boolean,
    expenseId: String?,
) = if (isSaving) {
    labels.saving
} else if (expenseId == null) {
    labels.saveExpense
} else {
    labels.updateExpense
}
