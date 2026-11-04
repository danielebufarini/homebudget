package it.danielebufarini.spesify.ui.screens

import it.danielebufarini.spesify.data.ExpenseRepository
import it.danielebufarini.spesify.data.IdGenerator
import it.danielebufarini.spesify.data.EditorItem
import it.danielebufarini.spesify.data.SaveExpenseCommand
import it.danielebufarini.spesify.data.TransactionEditorService
import it.danielebufarini.spesify.data.requireSuccess
import it.danielebufarini.spesify.data.formatAmountInput
import it.danielebufarini.spesify.data.parseAmountInput
import it.danielebufarini.spesify.database.CATEGORY_TYPE_EXPENSE
import it.danielebufarini.spesify.database.Category
import it.danielebufarini.spesify.database.Expense
import java.util.Locale

// Repository-facing snapshot loading and draft persistence for Android voice expense flows.

internal suspend fun loadAndroidVoiceExpenseSnapshot(
    repository: ExpenseRepository,
    resolveCategoryName: (Category) -> String
): AndroidVoiceExpenseSnapshot {
    repository.seedStarterCategoriesIfEmpty()

    val categorySnapshot = repository.getAllCategoriesSnapshot()
    val categories = categorySnapshot
        .asSequence()
        .filter { it.categoryType == CATEGORY_TYPE_EXPENSE && it.isArchived != 1L }
        .sortedBy { resolveCategoryName(it).lowercase(Locale.getDefault()) }
        .map { category ->
            AndroidVoiceExpenseCategory(
                id = category.id,
                name = resolveCategoryName(category)
            )
        }
        .toList()

    val categoriesById = categorySnapshot.associateBy(Category::id)

    val recentExpenses = repository.getAllExpensesSnapshot()
        .sortedByDescending(Expense::date)
        .take(120)
        .mapNotNull { expense ->
            val category = categoriesById[expense.categoryId] ?: return@mapNotNull null
            AndroidVoiceExpenseCandidate(
                id = expense.id,
                amountInput = formatAmountInput(expense.amount),
                categoryId = expense.categoryId,
                categoryName = resolveCategoryName(category),
                description = expense.description,
                date = expense.date,
                isShared = expense.isShared == 1L
            )
        }

    return AndroidVoiceExpenseSnapshot(
        categories = categories,
        recentExpenses = recentExpenses
    )
}

internal suspend fun persistAndroidVoiceExpenseDraft(
    draft: AndroidVoiceExpenseDraft,
    repository: ExpenseRepository,
    uiStrings: AndroidVoiceExpenseUiStrings,
    editorService: TransactionEditorService
) {
    val amount = parseAmountInput(draft.amountInput)
        ?: error(uiStrings.voiceExpenseInvalidAmount)
    require(amount > 0L) {
        uiStrings.voiceExpenseValueAmountPositive
    }

    val item = when (draft.action) {
        AndroidVoiceExpenseActionKind.Create -> EditorItem.New(buildAndroidVoiceExpenseId())
        AndroidVoiceExpenseActionKind.Update -> {
            val expenseId = draft.expenseId ?: error(uiStrings.expenseIdMissing)
            val existingExpense = repository.getExpenseById(expenseId) ?: error(uiStrings.expenseNotFound)
            EditorItem.Existing(existingExpense.id, existingExpense.recurringSeriesId)
        }
        AndroidVoiceExpenseActionKind.NeedClarification,
        AndroidVoiceExpenseActionKind.Ignore -> error(uiStrings.nothingToSave)
    }
    editorService.execute(SaveExpenseCommand(
        item = item, amount = amount, date = draft.date, categoryId = draft.categoryId,
        description = draft.description?.takeIf { it.isNotBlank() }, isShared = draft.isShared
    )).requireSuccess()
}

internal fun buildAndroidVoiceExpenseId(): String = IdGenerator.newId("voice-expense")
