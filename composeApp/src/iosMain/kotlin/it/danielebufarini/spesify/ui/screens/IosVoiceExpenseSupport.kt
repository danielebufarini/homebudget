package it.danielebufarini.spesify.ui.screens
import it.danielebufarini.spesify.data.ExpenseRepository
import it.danielebufarini.spesify.data.IdGenerator
import it.danielebufarini.spesify.data.formatAmountInput
import it.danielebufarini.spesify.data.parseAmountInput
import it.danielebufarini.spesify.database.CATEGORY_TYPE_EXPENSE
import it.danielebufarini.spesify.database.Category
import it.danielebufarini.spesify.localization.loadCategoryNameResolver
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString
import spesify.composeapp.generated.resources.Res
import spesify.composeapp.generated.resources.voice_expense_category_required
import spesify.composeapp.generated.resources.voice_expense_invalid_amount
import spesify.composeapp.generated.resources.voice_expense_value_amount_positive

internal suspend fun loadIosVoiceExpenseSnapshot(
    repository: ExpenseRepository
): IosVoiceExpenseSnapshot {
    repository.seedStarterCategoriesIfEmpty()
    val resolveCategoryName = loadCategoryNameResolver()

    val categorySnapshot = repository.getAllCategoriesSnapshot()
    val categories = categorySnapshot
        .asSequence()
        .filter { it.categoryType == CATEGORY_TYPE_EXPENSE && it.isArchived != 1L }
        .sortedBy { resolveCategoryName(it.id, it.name).lowercase() }
        .map { category ->
            IosVoiceExpenseCategory(
                id = category.id,
                name = resolveCategoryName(category.id, category.name)
            )
        }
        .toList()

    val categoriesById = categorySnapshot.associateBy(Category::id)
    val recentExpenses = repository.getRecentExpensesSnapshot(limit = 120)
        .mapNotNull { expense ->
            val category = categoriesById[expense.categoryId] ?: return@mapNotNull null
            IosVoiceExpenseRecord(
                id = expense.id,
                amountInput = formatAmountInput(expense.amount),
                categoryId = expense.categoryId,
                categoryName = resolveCategoryName(category.id, category.name),
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
): Pair<Long?, String?> {
    val parsedAmount = parseAmountInput(amountInput)
        ?: return null to getString(Res.string.voice_expense_invalid_amount)
    val error = when {
        parsedAmount <= 0L -> getString(Res.string.voice_expense_value_amount_positive)
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
