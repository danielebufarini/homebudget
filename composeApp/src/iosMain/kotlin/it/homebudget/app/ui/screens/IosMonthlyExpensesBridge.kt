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

class IosMonthlyExpenseRow(
    val id: String,
    val title: String,
    val dateText: String,
    val amountText: String
)

class IosMonthlyExpenseSection(
    val id: String,
    val title: String,
    val countLabel: String,
    val totalAmountText: String,
    val rows: List<IosMonthlyExpenseRow>
)

class IosMonthlyExpensesSnapshot(
    val totalAmountText: String,
    val emptyStateText: String,
    val sections: List<IosMonthlyExpenseSection>
)

class IosMonthlyExpensesObserver(
    private val year: Int,
    private val month: Int
) {
    private val scope = MainScope()
    private var updatesJob: Job? = null

    private val repository: ExpenseRepository by lazy {
        ensureKoinStarted()
        KoinPlatformTools.defaultContext().get().get<ExpenseRepository>()
    }

    fun start(onUpdate: (IosMonthlyExpensesSnapshot) -> Unit) {
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
                    localDate.year == year && localDate.month.ordinal + 1 == month
                }

                val sections = filteredExpenses
                    .groupBy { categoriesById[it.categoryId]?.name ?: "Unknown category" }
                    .toList()
                    .sortedBy { it.first }
                    .map { (categoryName, categoryExpenses) ->
                        IosMonthlyExpenseSection(
                            id = categoryName,
                            title = categoryName,
                            countLabel = "${categoryExpenses.size} expenses",
                            totalAmountText = formatAmount(categoryExpenses.map { it.amount }.sumBigInteger()),
                            rows = categoryExpenses
                                .sortedByDescending(Expense::date)
                                .map { expense ->
                                    IosMonthlyExpenseRow(
                                        id = expense.id,
                                        title = expense.description?.ifBlank { "Expense" } ?: "Expense",
                                        dateText = formatDate(expense.date),
                                        amountText = formatAmount(expense.amount)
                                    )
                                }
                        )
                    }

                IosMonthlyExpensesSnapshot(
                    totalAmountText = formatAmount(filteredExpenses.map { it.amount }.sumBigInteger()),
                    emptyStateText = "No expenses for this month",
                    sections = sections
                )
            }.collect { snapshot ->
                onUpdate(snapshot)
            }
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
