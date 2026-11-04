package it.danielebufarini.spesify.data

import kotlinx.coroutines.CancellationException

internal sealed interface EditorItem {
    data class New(val id: String? = null) : EditorItem
    data class Existing(val id: String, val recurringSeriesId: String? = null) : EditorItem
}

internal enum class EditorMutationScope { Single, Series }

internal sealed interface ExpenseSchedule {
    data object Single : ExpenseSchedule
    data class Installments(val count: Int) : ExpenseSchedule
    data object Monthly : ExpenseSchedule
}

internal data class SaveExpenseCommand(
    val item: EditorItem,
    val amount: Long,
    val date: Long,
    val categoryId: String,
    val description: String?,
    val isShared: Boolean,
    val schedule: ExpenseSchedule = ExpenseSchedule.Single,
    val scope: EditorMutationScope = EditorMutationScope.Single
)

internal data class SaveIncomeCommand(
    val item: EditorItem,
    val amount: Long,
    val date: Long,
    val categoryId: String?,
    val description: String?,
    val recurringMonthly: Boolean = false,
    val scope: EditorMutationScope = EditorMutationScope.Single
)

internal sealed interface EditorDeletion {
    data class Single(val id: String) : EditorDeletion
    data class Series(val id: String) : EditorDeletion
}

internal data class DeleteTransactionCommand(val kind: TransactionKind, val target: EditorDeletion)

internal sealed interface EditorOperationResult {
    data class Saved(val transactionIds: List<String>) : EditorOperationResult
    data object Deleted : EditorOperationResult
    data class Failed(val cause: Throwable) : EditorOperationResult
}

internal fun EditorOperationResult.requireSuccess() {
    when (this) {
        is EditorOperationResult.Saved, EditorOperationResult.Deleted -> Unit
        is EditorOperationResult.Failed -> throw cause
    }
}

/** Persistence orchestration only. Ingress adapters own parsing and normalization policies. */
internal class TransactionEditorService(
    private val repository: TransactionWriteRepository,
    private val newId: (String) -> String = IdGenerator::newId
) {
    suspend fun execute(command: SaveExpenseCommand): EditorOperationResult = operation {
        val existing = command.item as? EditorItem.Existing
        val seriesId = existing?.recurringSeriesId
        if (existing != null && seriesId != null && command.scope == EditorMutationScope.Series) {
            repository.updateRecurringExpenseSeries(
                existing.id, seriesId, command.amount, command.date, command.categoryId,
                command.description, command.isShared
            )
            return@operation EditorOperationResult.Saved(listOf(existing.id))
        }
        val expenses = when {
            existing != null && seriesId != null -> listOf(command.toPending(existing.id, seriesId))
            command.schedule == ExpenseSchedule.Monthly -> {
                val newSeriesId = newId("recurring-expense")
                if (existing != null) {
                    buildRecurringMonthlyExpensesFromExistingExpense(
                        existing.id, command.amount, command.date, command.categoryId,
                        command.description, command.isShared, newSeriesId, { newId("expense") }
                    )
                } else {
                    buildRecurringMonthlyExpenses(
                        command.amount, command.date, command.categoryId, command.description,
                        command.isShared, newSeriesId, expenseIds(command.item)
                    )
                }
            }
            existing == null && command.schedule is ExpenseSchedule.Installments -> buildPendingExpenses(
                command.amount, command.date, command.schedule.count, command.categoryId,
                command.description, command.isShared, expenseIds(command.item)
            )
            else -> listOf(command.toPending(itemId(command.item, "expense"), null))
        }
        repository.insertExpenses(expenses)
        EditorOperationResult.Saved(expenses.map(PendingExpense::id))
    }

    suspend fun execute(command: SaveIncomeCommand): EditorOperationResult = operation {
        val existing = command.item as? EditorItem.Existing
        val seriesId = existing?.recurringSeriesId
        if (existing != null && seriesId != null && command.scope == EditorMutationScope.Series) {
            repository.updateRecurringIncomeSeries(
                existing.id, seriesId, command.amount, command.date, command.description, command.categoryId
            )
            return@operation EditorOperationResult.Saved(listOf(existing.id))
        }
        val incomes = if (seriesId == null && command.recurringMonthly) {
            buildRecurringMonthlyIncomes(
                amount = command.amount, firstDate = command.date, description = command.description,
                categoryId = command.categoryId, recurringSeriesId = newId("recurring-income"),
                idProvider = { newId("income") }
            ).mapIndexed { index, income ->
                val firstId = when (val item = command.item) {
                    is EditorItem.New -> item.id
                    is EditorItem.Existing -> item.id
                }
                if (index == 0 && firstId != null) income.copy(id = firstId) else income
            }
        } else {
            listOf(PendingIncome(
                id = itemId(command.item, "income"), amount = command.amount, date = command.date,
                description = command.description, categoryId = command.categoryId, recurringSeriesId = seriesId
            ))
        }
        repository.insertIncomes(incomes)
        EditorOperationResult.Saved(incomes.map(PendingIncome::id))
    }

    suspend fun execute(command: DeleteTransactionCommand): EditorOperationResult = operation {
        when (command.kind) {
            TransactionKind.Expense -> when (val target = command.target) {
                is EditorDeletion.Single -> repository.deleteExpense(target.id)
                is EditorDeletion.Series -> repository.deleteRecurringExpenseSeries(target.id)
            }
            TransactionKind.Income -> when (val target = command.target) {
                is EditorDeletion.Single -> repository.deleteIncome(target.id)
                is EditorDeletion.Series -> repository.deleteRecurringIncomeSeries(target.id)
            }
        }
        EditorOperationResult.Deleted
    }

    private fun itemId(item: EditorItem, prefix: String): String = when (item) {
        is EditorItem.New -> item.id ?: newId(prefix)
        is EditorItem.Existing -> item.id
    }

    private fun expenseIds(item: EditorItem): () -> String {
        var first = true
        return {
            val explicitId = (item as? EditorItem.New)?.id.takeIf { first }
            first = false
            explicitId ?: newId("expense")
        }
    }

    private suspend inline fun operation(block: () -> EditorOperationResult): EditorOperationResult = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        EditorOperationResult.Failed(failure)
    }
}

private fun SaveExpenseCommand.toPending(id: String, seriesId: String?) = PendingExpense(
    id, amount, date, categoryId, description, isShared, seriesId
)
