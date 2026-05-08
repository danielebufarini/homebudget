package it.homebudget.app.ui.screens

import com.ionspin.kotlin.bignum.integer.BigInteger
import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.voice_expense_category_required
import homebudget.composeapp.generated.resources.voice_expense_invalid_amount
import homebudget.composeapp.generated.resources.voice_expense_value_amount_positive
import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.data.IdGenerator
import it.homebudget.app.data.formatAmountInput
import it.homebudget.app.data.parseAmountInput
import it.homebudget.app.database.Category
import it.homebudget.app.database.Expense
import it.homebudget.app.localization.loadCategoryNameResolver
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString

internal suspend fun loadIosVoiceExpenseSnapshot(
    repository: ExpenseRepository
): IosVoiceExpenseSnapshot {
    repository.insertDefaultCategoriesIfEmpty()
    val resolveCategoryName = loadCategoryNameResolver()

    val categorySnapshot = repository.getAllCategoriesSnapshot()
    val categories = categorySnapshot
        .sortedBy { resolveCategoryName(it.id, it.name, it.isCustom).lowercase() }
        .map { category ->
            IosVoiceExpenseCategory(
                id = category.id,
                name = resolveCategoryName(category.id, category.name, category.isCustom)
            )
        }

    val categoriesById = categorySnapshot.associateBy(Category::id)
    val recentExpenses = repository.getAllExpensesSnapshot()
        .sortedByDescending(Expense::date)
        .take(120)
        .mapNotNull { expense ->
            val category = categoriesById[expense.categoryId] ?: return@mapNotNull null
            IosVoiceExpenseRecord(
                id = expense.id,
                amountInput = formatAmountInput(expense.amount),
                categoryId = expense.categoryId,
                categoryName = resolveCategoryName(category.id, category.name, category.isCustom),
                description = expense.description,
                date = expense.date,
                isShared = expense.isShared == 1L
            )
        }

    return IosVoiceExpenseSnapshot(
        categories = categories,
        recentExpenses = recentExpenses
    )
}

internal suspend fun validateIosVoiceExpenseInput(
    amountInput: String,
    categoryId: String
): Pair<BigInteger?, String?> {
    val parsedAmount = parseAmountInput(amountInput)
        ?: return null to getString(Res.string.voice_expense_invalid_amount)
    val error = when {
        parsedAmount <= BigInteger.ZERO -> getString(Res.string.voice_expense_value_amount_positive)
        categoryId.isBlank() -> getString(Res.string.voice_expense_category_required)
        else -> null
    }
    return parsedAmount to error
}

internal fun buildIosVoiceExpenseId(): String = IdGenerator.newId("voice-expense")

internal fun normalizeIosVoiceExpenseDate(date: Long): Long {
    val localDate = kotlin.time.Instant.fromEpochMilliseconds(date)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
    return localDate.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
}
