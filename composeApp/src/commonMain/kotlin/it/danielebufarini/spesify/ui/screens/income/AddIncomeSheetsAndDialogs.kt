package it.danielebufarini.spesify.ui.screens.income

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import it.danielebufarini.spesify.data.CategoryManagementRepository
import it.danielebufarini.spesify.database.CATEGORY_TYPE_INCOME
import it.danielebufarini.spesify.database.Category
import it.danielebufarini.spesify.ui.screens.categories.AddCategorySheet
import it.danielebufarini.spesify.ui.screens.categories.DEFAULT_CATEGORY_ICON_KEY
import it.danielebufarini.spesify.ui.screens.categories.buildCategoryId
import it.danielebufarini.spesify.ui.screens.expenses.CategoryPickerSheet
import it.danielebufarini.spesify.ui.screens.transactions.RecurringSeriesActionDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun AddIncomeSheetsAndDialogs(
    labels: AddIncomeRouteLabels,
    repository: CategoryManagementRepository,
    scopeLaunch: ((suspend () -> Unit) -> Unit),
    showAddCategorySheet: Boolean,
    onAddCategorySheetChange: (Boolean) -> Unit,
    showCategoryPickerSheet: Boolean,
    onCategoryPickerSheetChange: (Boolean) -> Unit,
    selectableCategories: List<Category>,
    selectedCategoryId: String?,
    onCategorySelected: (String) -> Unit,
    resolveCategoryName: (Category) -> String,
    pendingRecurringAction: RecurringIncomeAction?,
    pendingRecurringUpdate: PendingRecurringIncomeUpdate?,
    onDismissRecurringDialog: () -> Unit,
    onSavingChange: (Boolean) -> Unit,
    saveIncomeUpdate: suspend (Boolean, PendingRecurringIncomeUpdate) -> Unit,
    deleteIncome: suspend (Boolean) -> Unit,
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
                                categoryType = CATEGORY_TYPE_INCOME,
                            )
                        }
                        onCategorySelected(categoryId)
                    }.onSuccess {
                        onAddCategorySheetChange(false)
                    }.onFailure {
                        snackbarHostState.showSnackbar(labels.unableToSaveIncome)
                    }
                }
            },
        )
    }

    if (showCategoryPickerSheet) {
        CategoryPickerSheet(
            categories = selectableCategories,
            selectedCategoryId = selectedCategoryId.orEmpty(),
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
                RecurringIncomeAction.Update -> labels.updateRecurringIncomeTitle
                RecurringIncomeAction.Delete -> labels.deleteRecurringIncomeTitle
            },
            message = when (pendingRecurringAction) {
                RecurringIncomeAction.Update -> labels.recurringIncomeActionUpdate
                RecurringIncomeAction.Delete -> labels.recurringIncomeActionDelete
            },
            onThisInstanceOnly = {
                scopeLaunch {
                    onSavingChange(true)
                    when (pendingRecurringAction) {
                        RecurringIncomeAction.Update -> pendingRecurringUpdate?.let {
                            saveIncomeUpdate(false, it)
                        } ?: onSavingChange(false)
                        RecurringIncomeAction.Delete -> deleteIncome(false)
                    }
                }
            },
            onWholeSeries = {
                scopeLaunch {
                    onSavingChange(true)
                    when (pendingRecurringAction) {
                        RecurringIncomeAction.Update -> pendingRecurringUpdate?.let {
                            saveIncomeUpdate(true, it)
                        } ?: onSavingChange(false)
                        RecurringIncomeAction.Delete -> deleteIncome(true)
                    }
                }
            },
            onDismiss = onDismissRecurringDialog,
        )
    }
}
