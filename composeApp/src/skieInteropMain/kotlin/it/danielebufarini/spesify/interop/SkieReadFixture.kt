package it.danielebufarini.spesify.interop

import it.danielebufarini.spesify.ui.screens.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.job

/** Controlled inputs to the production observers, never a substitute error boundary. */
class SkieReadFixture {
    private enum class Command { Data, Fail, Complete }

    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val started = MutableStateFlow(0)
    private val cleaned = MutableStateFlow(0)
    private val active = MutableStateFlow(0)
    private val maximumActive = MutableStateFlow(0)
    private val producer = MutableStateFlow<Job?>(null)
    private val categoryType = MutableStateFlow("")

    val activeCollectors: Int get() = active.value
    val maximumActiveCollectors: Int get() = maximumActive.value
    val startedCollectors: Int get() = started.value
    val cleanedCollectors: Int get() = cleaned.value
    val lastCategoryType: String get() = categoryType.value

    val groupedObserver = IosGroupedExpensesObserver(ExpenseGroupingMode.ByCategory) {
        controlled(IosGroupedExpensesSnapshot("12.34", "empty", listOf(
            IosGroupedExpenseSection("Food", "Food", "food", "restaurant", "12.34", listOf(
                IosGroupedExpenseRow("expense", "Lunch", "2026-11-05", "12.34", "food", "restaurant", null)
            ))
        )))
    }
    val incomesObserver = IosMonthlyIncomesObserver(ExpenseGroupingMode.ByDate) {
        controlled(IosMonthlyIncomesSnapshot("12.34", "empty", listOf(
            IosIncomeSection("date:5 Nov 2026", "5 Nov 2026", null, null, "12.34", listOf(
                IosIncomeRow("income", "Salary", "2026-11-05", "12.34", null, null, null)
            ))
        )))
    }
    val recurringObserver = IosRecurringExpensesObserver {
        controlled(IosRecurringExpensesSnapshot("12.34 / month", "Total recurring", "Recurring expenses", "empty", "empty details", listOf(
            IosGroupedExpenseRow("recurring", "Rent", "monthly", "12.34", "home", "home", "series")
        )))
    }
    val categoriesController = IosCategoriesController { type ->
        categoryType.value = type
        controlled(IosCategoriesSnapshot(listOf(IosCategoryItem(type, "Category $type", "category"))))
    }

    fun sendData() { check(commands.trySend(Command.Data).isSuccess) }
    fun failRead() { check(commands.trySend(Command.Fail).isSuccess) }
    fun completeRead() { check(commands.trySend(Command.Complete).isSuccess) }
    fun cancelFromKotlin() { producer.value?.cancel() }

    suspend fun awaitStarted(count: Int) { started.first { it >= count } }
    suspend fun awaitCleanup(count: Int) { cleaned.first { it >= count } }

    private fun <T> controlled(snapshot: T): Flow<T> = flow {
        producer.value = currentCoroutineContext().job
        val activeCount = active.updateAndGet { it + 1 }
        maximumActive.update { maxOf(it, activeCount) }
        started.update { it + 1 }
        try {
            for (command in commands) {
                when (command) {
                    Command.Data -> emit(snapshot)
                    Command.Fail -> error("Controlled database read failure")
                    Command.Complete -> return@flow
                }
            }
        } finally {
            active.update { it - 1 }
            cleaned.update { it + 1 }
        }
    }
}
