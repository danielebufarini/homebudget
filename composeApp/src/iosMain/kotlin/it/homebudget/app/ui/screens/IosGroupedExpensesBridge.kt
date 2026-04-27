package it.homebudget.app.ui.screens

import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.data.formatAmount
import it.homebudget.app.data.sumBigInteger
import it.homebudget.app.database.Expense
import it.homebudget.app.di.initKoin
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.mp.KoinPlatformTools
import kotlin.time.Instant

class IosGroupedExpenseRow(
    val id: String,
    val title: String,
    val dateText: String,
    val amountText: String
)

class IosGroupedExpenseSection(
    val id: String,
    val title: String,
    val countLabel: String,
    val totalAmountText: String,
    val rows: List<IosGroupedExpenseRow>
)

class IosGroupedExpensesSnapshot(
    val totalAmountText: String,
    val emptyStateText: String,
    val sections: List<IosGroupedExpenseSection>
)

class IosGroupedExpensesObserver(
    private val year: Int,
    private val month: Int,
    private val screenType: String,
    private val categoryName: String?
) {
    private val scope = MainScope()
    private var updatesJob: Job? = null

    private val repository: ExpenseRepository by lazy {
        ensureKoinStarted()
        KoinPlatformTools.defaultContext().get().get<ExpenseRepository>()
    }

    fun start(onUpdate: (IosGroupedExpensesSnapshot) -> Unit) {
        if (updatesJob != null) {
            return
        }

        updatesJob = scope.launch {
            repository.insertDefaultCategoriesIfEmpty()
            combine(
                repository.getAllExpenses(),
                repository.getAllCategories()
            ) { expenses, categories ->
                val categoriesById = categories.associateBy { it.id }
                val filteredExpenses = expenses.filter { expense ->
                    val localDate = expense.date.toLocalDate()
                    localDate.year == year &&
                        localDate.month.ordinal + 1 == month &&
                        includeExpense(expense)
                }

                val sections = filteredExpenses
                    .groupBy { categoriesById[it.categoryId]?.name ?: "Unknown category" }
                    .filterKeys(::includeCategory)
                    .toList()
                    .sortedBy { it.first }
                    .map { (groupName, groupExpenses) ->
                        IosGroupedExpenseSection(
                            id = groupName,
                            title = groupName,
                            countLabel = countLabel(groupExpenses.size),
                            totalAmountText = formatAmount(groupExpenses.map { it.amount }.sumBigInteger()),
                            rows = groupExpenses
                                .sortedByDescending(Expense::date)
                                .map { expense ->
                                    IosGroupedExpenseRow(
                                        id = expense.id,
                                        title = expense.description?.ifBlank { expenseFallbackTitle() }
                                            ?: expenseFallbackTitle(),
                                        dateText = formatDate(expense.date),
                                        amountText = formatAmount(expense.amount)
                                    )
                                }
                        )
                    }

                IosGroupedExpensesSnapshot(
                    totalAmountText = formatAmount(filteredExpenses.map { it.amount }.sumBigInteger()),
                    emptyStateText = emptyStateText(),
                    sections = sections
                )
            }.collect { snapshot ->
                onUpdate(snapshot)
            }
        }
    }

    fun deleteExpense(id: String) {
        scope.launch {
            repository.deleteExpense(id)
        }
    }

    fun stop() {
        updatesJob?.cancel()
        updatesJob = null
    }

    fun dispose() {
        stop()
        scope.cancel()
    }

    private fun includeExpense(expense: Expense): Boolean = when (screenType) {
        "shared" -> expense.isShared == 1L
        else -> true
    }

    private fun includeCategory(groupName: String): Boolean = when (screenType) {
        "category" -> groupName == categoryName
        else -> true
    }

    private fun countLabel(count: Int): String = when (screenType) {
        "shared" -> "$count shared expenses"
        else -> "$count expenses"
    }

    private fun expenseFallbackTitle(): String = when (screenType) {
        "shared" -> "Shared expense"
        else -> "Expense"
    }

    private fun emptyStateText(): String = when (screenType) {
        "shared" -> "No shared expenses for this month"
        "category" -> "No expenses for ${categoryName ?: "this category"} this month"
        else -> "No expenses for this month"
    }

    private fun formatDate(epochMillis: Long): String {
        val date = epochMillis.toLocalDate()
        return "${date.year}-${(date.month.ordinal + 1).toString().padStart(2, '0')}-${date.day.toString().padStart(2, '0')}"
    }

    private fun Long.toLocalDate() = Instant.fromEpochMilliseconds(this)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
}

private fun ensureKoinStarted() {
    if (KoinPlatformTools.defaultContext().getOrNull() == null) {
        initKoin()
    }
}
