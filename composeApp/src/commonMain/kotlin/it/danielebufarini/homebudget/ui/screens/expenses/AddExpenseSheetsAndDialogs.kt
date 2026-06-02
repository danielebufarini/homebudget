package it.danielebufarini.homebudget.ui.screens.expenses

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import it.danielebufarini.homebudget.data.ExpenseRepository
import it.danielebufarini.homebudget.database.CATEGORY_TYPE_EXPENSE
import it.danielebufarini.homebudget.database.Category
import it.danielebufarini.homebudget.ui.screens.categories.AddCategorySheet
import it.danielebufarini.homebudget.ui.screens.categories.DEFAULT_CATEGORY_ICON_KEY
import it.danielebufarini.homebudget.ui.screens.categories.buildCategoryId
import it.danielebufarini.homebudget.ui.screens.transactions.RecurringSeriesActionDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun AddExpenseSheetsAndDialogs(
    labels: AddExpenseRouteLabels,
    repository: ExpenseRepository,
    scopeLaunch: ((suspend () -> Unit) -> Unit),
    showAddCategorySheet: Boolean,
    onAddCategorySheetChange: (Boolean) -> Unit,
    showCategoryPickerSheet: Boolean,
    onCategoryPickerSheetChange: (Boolean) -> Unit,
    selectableCategories: List<Category>,
    selectedCategoryId: String,
    onCategorySelected: (String) -> Unit,
    resolveCategoryName: (Category) -> String,
    pendingRecurringAction: RecurringExpenseAction?,
    pendingRecurringUpdate: PendingRecurringExpenseUpdate?,
    onDismissRecurringDialog: () -> Unit,
    onSavingChange: (Boolean) -> Unit,
    saveExpenseUpdate: suspend (Boolean, PendingRecurringExpenseUpdate) -> Unit,
    deleteExpense: suspend (Boolean) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    if (showAddCategorySheet) {
        AddCategorySheet(
            onDismiss = { onAddCategorySheetChange(false) },
            title = labels.addCategory,
            confirmLabel = labels.add,
            initialName = "",
            initialIconKey = DEFAULT_CATEGORY_ICON_KEY,
            onConfirm = { name, iconKey ->
                scopeLaunch {
                    runCatching {
                        val categoryId = buildCategoryId()
                        withContext(Dispatchers.Default) {
                            repository.insertCategory(
                                id = categoryId,
                                name = name,
                                icon = iconKey,
                                categoryType = CATEGORY_TYPE_EXPENSE,
                            )
                        }
                        onCategorySelected(categoryId)
                    }.onSuccess {
                        onAddCategorySheetChange(false)
                    }.onFailure {
                        snackbarHostState.showSnackbar(labels.unableToSaveExpense)
                    }
                }
            },
        )
    }

    if (showCategoryPickerSheet) {
        CategoryPickerSheet(
            categories = selectableCategories,
            selectedCategoryId = selectedCategoryId,
            resolveCategoryName = resolveCategoryName,
            onDismiss = { onCategoryPickerSheetChange(false) },
            onAddCategory = {
                onCategoryPickerSheetChange(false)
                onAddCategorySheetChange(true)
            },
            onCategorySelected = { categoryId ->
                onCategorySelected(categoryId)
                onCategoryPickerSheetChange(false)
            },
        )
    }

    if (pendingRecurringAction != null) {
        RecurringSeriesActionDialog(
            title = when (pendingRecurringAction) {
                RecurringExpenseAction.Update -> labels.updateRecurringExpenseTitle
                RecurringExpenseAction.Delete -> labels.deleteRecurringExpenseTitle
            },
            message = when (pendingRecurringAction) {
                RecurringExpenseAction.Update -> labels.recurringExpenseActionUpdate
                RecurringExpenseAction.Delete -> labels.recurringExpenseActionDelete
            },
            onThisInstanceOnly = {
                scopeLaunch {
                    onSavingChange(true)
                    when (pendingRecurringAction) {
                        RecurringExpenseAction.Update -> pendingRecurringUpdate?.let {
                            saveExpenseUpdate(false, it)
                        } ?: onSavingChange(false)
                        RecurringExpenseAction.Delete -> deleteExpense(false)
                    }
                }
            },
            onWholeSeries = {
                scopeLaunch {
                    onSavingChange(true)
                    when (pendingRecurringAction) {
                        RecurringExpenseAction.Update -> pendingRecurringUpdate?.let {
                            saveExpenseUpdate(true, it)
                        } ?: onSavingChange(false)
                        RecurringExpenseAction.Delete -> deleteExpense(true)
                    }
                }
            },
            onDismiss = onDismissRecurringDialog,
        )
    }
}
