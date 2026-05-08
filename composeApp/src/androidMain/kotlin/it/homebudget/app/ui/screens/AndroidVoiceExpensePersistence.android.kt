package it.homebudget.app.ui.screens

import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.data.IdGenerator
import it.homebudget.app.data.PendingExpense
import it.homebudget.app.data.formatAmountInput
import it.homebudget.app.data.parseAmountInput
import it.homebudget.app.database.Category
import it.homebudget.app.database.Expense
import java.util.Locale

// Repository-facing snapshot loading and draft persistence for Android voice expense flows.

internal suspend fun loadAndroidVoiceExpenseSnapshot(
    repository: ExpenseRepository,
    resolveCategoryName: (Category) -> String
): AndroidVoiceExpenseSnapshot {
    repository.insertDefaultCategoriesIfEmpty()

    val categorySnapshot = repository.getAllCategoriesSnapshot()
    val categories = categorySnapshot
        .sortedBy { resolveCategoryName(it).lowercase(Locale.getDefault()) }
        .map { category ->
            AndroidVoiceExpenseCategory(
                id = category.id,
                name = resolveCategoryName(category)
            )
        }

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
    uiStrings: AndroidVoiceExpenseUiStrings
) {
    val amount = parseAmountInput(draft.amountInput)
        ?: error(uiStrings.voiceExpenseInvalidAmount)
    require(amount > com.ionspin.kotlin.bignum.integer.BigInteger.ZERO) {
        uiStrings.voiceExpenseValueAmountPositive
    }

    when (draft.action) {
        AndroidVoiceExpenseActionKind.Create -> {
            repository.insertExpenses(
                expenses = listOf(
                    PendingExpense(
                        id = buildAndroidVoiceExpenseId(),
                        amount = amount,
                        date = draft.date,
                        categoryId = draft.categoryId,
                        description = draft.description?.takeIf { it.isNotBlank() },
                        isShared = draft.isShared,
                        recurringSeriesId = null
                    )
                )
            )
        }

        AndroidVoiceExpenseActionKind.Update -> {
            val expenseId = draft.expenseId ?: error(uiStrings.expenseIdMissing)
            val existingExpense = repository.getExpenseById(expenseId) ?: error(uiStrings.expenseNotFound)
            repository.insertExpenses(
                expenses = listOf(
                    PendingExpense(
                        id = existingExpense.id,
                        amount = amount,
                        date = draft.date,
                        categoryId = draft.categoryId,
                        description = draft.description?.takeIf { it.isNotBlank() },
                        isShared = draft.isShared,
                        recurringSeriesId = existingExpense.recurringSeriesId
                    )
                )
            )
        }

        AndroidVoiceExpenseActionKind.Ignore -> {
            error(uiStrings.nothingToSave)
        }
    }
}

internal fun buildAndroidVoiceExpenseId(): String = IdGenerator.newId("voice-expense")
