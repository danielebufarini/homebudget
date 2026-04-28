package it.homebudget.app.ui.screens

import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.data.PendingExpense
import it.homebudget.app.data.formatAmountInput
import it.homebudget.app.data.parseAmountInput
import it.homebudget.app.di.initKoin
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.koin.mp.KoinPlatformTools
import kotlin.random.Random
import kotlin.time.Clock

class IosVoiceExpenseCategory(
    val id: String,
    val name: String
)

class IosVoiceExpenseRecord(
    val id: String,
    val amountInput: String,
    val categoryId: String,
    val categoryName: String,
    val description: String?,
    val date: Long,
    val isShared: Boolean
)

class IosVoiceExpenseSnapshot(
    val categories: List<IosVoiceExpenseCategory>,
    val recentExpenses: List<IosVoiceExpenseRecord>
)

class IosVoiceExpenseController {
    private val scope = MainScope()
    private val repository: ExpenseRepository by lazy {
        if (KoinPlatformTools.defaultContext().getOrNull() == null) {
            initKoin()
        }
        KoinPlatformTools.defaultContext().get().get<ExpenseRepository>()
    }

    fun loadSnapshot(
        onResult: (IosVoiceExpenseSnapshot?) -> Unit
    ) {
        scope.launch {
            val result = runCatching {
                repository.insertDefaultCategoriesIfEmpty()

                val categorySnapshot = repository.getAllCategoriesSnapshot()
                val categories = categorySnapshot
                    .sortedBy { it.name.lowercase() }
                    .map { category ->
                        IosVoiceExpenseCategory(
                            id = category.id,
                            name = category.name
                        )
                    }

                val categoriesById = categorySnapshot.associateBy { it.id }

                val recentExpenses = repository.getAllExpensesSnapshot()
                    .sortedByDescending { it.date }
                    .take(120)
                    .mapNotNull { expense ->
                        val category = categoriesById[expense.categoryId] ?: return@mapNotNull null
                        IosVoiceExpenseRecord(
                            id = expense.id,
                            amountInput = formatAmountInput(expense.amount),
                            categoryId = expense.categoryId,
                            categoryName = category.name,
                            description = expense.description,
                            date = expense.date,
                            isShared = expense.isShared == 1L
                        )
                    }

                IosVoiceExpenseSnapshot(
                    categories = categories,
                    recentExpenses = recentExpenses
                )
            }

            onResult(result.getOrNull())
        }
    }

    fun createExpense(
        amountInput: String,
        categoryId: String,
        description: String?,
        date: Long,
        isShared: Boolean,
        onComplete: (Boolean, String?) -> Unit
    ) {
        scope.launch {
            val result = runExpenseSave(
                amountInput = amountInput,
                categoryId = categoryId
            ) { amount ->
                repository.insertExpenses(
                    listOf(
                        PendingExpense(
                            id = buildExpenseId(),
                            amount = amount,
                            date = normalizeToStartOfDay(date),
                            categoryId = categoryId,
                            description = description?.takeIf { it.isNotBlank() },
                            isShared = isShared,
                            recurringSeriesId = null
                        )
                    )
                )
            }
            onComplete(result.first, result.second)
        }
    }

    fun updateExpense(
        expenseId: String,
        amountInput: String,
        categoryId: String,
        description: String?,
        date: Long,
        isShared: Boolean,
        onComplete: (Boolean, String?) -> Unit
    ) {
        scope.launch {
            val result = runExpenseSave(
                amountInput = amountInput,
                categoryId = categoryId
            ) { amount ->
                val existingExpense = repository.getExpenseById(expenseId)
                    ?: error("Expense not found")
                repository.insertExpenses(
                    listOf(
                        PendingExpense(
                            id = existingExpense.id,
                            amount = amount,
                            date = normalizeToStartOfDay(date),
                            categoryId = categoryId,
                            description = description?.takeIf { it.isNotBlank() },
                            isShared = isShared,
                            recurringSeriesId = existingExpense.recurringSeriesId
                        )
                    )
                )
            }
            onComplete(result.first, result.second)
        }
    }

    fun dispose() {
        scope.cancel()
    }

    private suspend fun runExpenseSave(
        amountInput: String,
        categoryId: String,
        block: suspend (com.ionspin.kotlin.bignum.integer.BigInteger) -> Unit
    ): Pair<Boolean, String?> {
        val parsedAmount = parseAmountInput(amountInput)
            ?: return false to "Invalid amount"
        if (parsedAmount <= com.ionspin.kotlin.bignum.integer.BigInteger.ZERO) {
            return false to "Amount must be greater than zero"
        }
        if (categoryId.isBlank()) {
            return false to "Category is required"
        }

        return runCatching {
            block(parsedAmount)
        }.fold(
            onSuccess = { true to null },
            onFailure = { false to (it.message ?: "Unable to save expense") }
        )
    }

    private fun buildExpenseId(): String {
        return "${Clock.System.now().toEpochMilliseconds()}-${Random.nextLong()}"
    }

    private fun normalizeToStartOfDay(date: Long): Long {
        val localDate = kotlin.time.Instant.fromEpochMilliseconds(date)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
        return localDate.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
    }
}
