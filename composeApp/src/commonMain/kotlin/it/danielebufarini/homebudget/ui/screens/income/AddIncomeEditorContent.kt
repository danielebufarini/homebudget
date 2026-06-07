package it.danielebufarini.homebudget.ui.screens.income

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import it.danielebufarini.homebudget.ui.screens.transactions.DateIcon
import it.danielebufarini.homebudget.ui.screens.transactions.DescriptionIcon
import it.danielebufarini.homebudget.ui.screens.transactions.RecurringIcon
import it.danielebufarini.homebudget.ui.screens.transactions.RecurringSeriesNotice
import it.danielebufarini.homebudget.ui.screens.transactions.SoftActionBar
import it.danielebufarini.homebudget.ui.screens.transactions.SoftCategoryPickerRow
import it.danielebufarini.homebudget.ui.screens.transactions.SoftInlineInfoCard
import it.danielebufarini.homebudget.ui.screens.transactions.SoftPickerRow
import it.danielebufarini.homebudget.ui.screens.transactions.SoftSectionCard
import it.danielebufarini.homebudget.ui.screens.transactions.SoftTextField
import it.danielebufarini.homebudget.ui.screens.transactions.SoftToggleRow
import it.danielebufarini.homebudget.ui.screens.transactions.TransactionAmountHeader
import it.danielebufarini.homebudget.ui.screens.transactions.TransactionEditorKind
import it.danielebufarini.homebudget.ui.screens.transactions.TransactionEditorSkeleton
import it.danielebufarini.homebudget.ui.screens.transactions.dismissKeyboardOnOutsideTap
import it.danielebufarini.homebudget.ui.screens.transactions.rememberKeyboardDismissAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddIncomeEditorContent(
    labels: AddIncomeRouteLabels,
    snackbarHostState: SnackbarHostState,
    showNavigationChrome: Boolean,
    isIos: Boolean,
    useFloatingBottomBar: Boolean,
    useIosHostedFloatingChrome: Boolean,
    contentTopPadding: Dp,
    contentBottomPadding: Dp,
    isInitialized: Boolean,
    incomeId: String?,
    isSaving: Boolean,
    amount: String,
    isAmountValid: Boolean,
    onAmountChange: (String) -> Unit,
    selectedCategoryName: String?,
    selectedCategoryIconKey: String?,
    selectedCategoryId: String?,
    onSelectCategory: () -> Unit,
    selectedDateMillis: Long,
    onSelectDate: () -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    isRecurringMonthly: Boolean,
    onRecurringMonthlyChange: (Boolean) -> Unit,
    recurringSeriesId: String?,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val dismissKeyboard = rememberKeyboardDismissAction()

    Scaffold(
        containerColor = if (useFloatingBottomBar) Color.Transparent else MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            if (showNavigationChrome) {
                AddIncomeTopBar(
                    labels = labels,
                    isIos = isIos,
                    incomeId = incomeId,
                    deleteEnabled = !isSaving,
                    onClose = onClose,
                    onDelete = onDelete,
                )
            }
        },
        bottomBar = {
            if (isInitialized && !useIosHostedFloatingChrome) {
                SoftActionBar(
                    cancelLabel = labels.cancel,
                    confirmLabel = if (incomeId == null) labels.save else labels.update,
                    confirmEnabled = !isSaving && isAmountValid,
                    onCancel = onClose,
                    onConfirm = onSave,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .navigationBarsPadding(),
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .dismissKeyboardOnOutsideTap(dismissKeyboard)
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
                AddIncomeForm(
                    labels = labels,
                    incomeId = incomeId,
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
                    onDismissKeyboard = dismissKeyboard,
                    isRecurringMonthly = isRecurringMonthly,
                    onRecurringMonthlyChange = onRecurringMonthlyChange,
                    recurringSeriesId = recurringSeriesId,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddIncomeTopBar(
    labels: AddIncomeRouteLabels,
    isIos: Boolean,
    incomeId: String?,
    deleteEnabled: Boolean,
    onClose: () -> Unit,
    onDelete: () -> Unit,
) {
    TopAppBar(
        title = { Text(if (incomeId == null) labels.addIncome else labels.editIncome) },
        navigationIcon = {
            if (isIos) {
                TextButton(onClick = onClose) {
                    Text(labels.back)
                }
            }
        },
        actions = {
            if (!isIos && incomeId != null) {
                IconButton(onClick = onDelete, enabled = deleteEnabled) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = labels.deleteIncome,
                    )
                }
            }
        },
    )
}

@Composable
private fun AddIncomeForm(
    labels: AddIncomeRouteLabels,
    incomeId: String?,
    amount: String,
    onAmountChange: (String) -> Unit,
    selectedCategoryName: String?,
    selectedCategoryIconKey: String?,
    selectedCategoryId: String?,
    onSelectCategory: () -> Unit,
    selectedDateMillis: Long,
    onSelectDate: () -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    onDismissKeyboard: () -> Unit,
    isRecurringMonthly: Boolean,
    onRecurringMonthlyChange: (Boolean) -> Unit,
    recurringSeriesId: String?,
) {
    TransactionAmountHeader(
        value = amount,
        onValueChange = onAmountChange,
        label = labels.amount,
        kind = TransactionEditorKind.Income,
    )

    SoftSectionCard(title = labels.details) {
        SoftCategoryPickerRow(
            label = labels.category,
            categoryName = selectedCategoryName,
            categoryIconKey = selectedCategoryIconKey,
            categoryColorKey = selectedCategoryId,
            placeholder = labels.selectCategory,
            onClick = onSelectCategory,
        )
        SoftPickerRow(
            label = labels.date,
            value = selectedDateMillis.formatIncomeDateLabel(),
            icon = DateIcon,
            onClick = onSelectDate,
        )
        SoftTextField(
            value = description,
            onValueChange = onDescriptionChange,
            label = labels.description,
            leadingIcon = DescriptionIcon,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDismissKeyboard() }),
        )
    }

    SoftSectionCard(title = labels.options) {
        if (incomeId == null) {
            SoftToggleRow(
                label = labels.recurringMonthly,
                description = null,
                icon = RecurringIcon,
                checked = isRecurringMonthly,
                onCheckedChange = onRecurringMonthlyChange,
            )
            SoftInlineInfoCard(
                visible = isRecurringMonthly,
                text = labels.recurringIncomeInfo,
                icon = RecurringIcon,
            )
        }

        if (recurringSeriesId != null) {
            RecurringSeriesNotice(text = labels.recurringIncomeSeriesInfo)
        }
    }
}
