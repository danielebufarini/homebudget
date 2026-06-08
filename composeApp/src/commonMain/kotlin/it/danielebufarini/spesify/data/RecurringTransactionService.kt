package it.danielebufarini.spesify.data

import it.danielebufarini.spesify.database.RECURRING_TRANSACTION_FREQUENCY_MONTHLY
import it.danielebufarini.spesify.database.RECURRING_TRANSACTION_KIND_EXPENSE
import it.danielebufarini.spesify.database.RECURRING_TRANSACTION_KIND_INCOME
import it.danielebufarini.spesify.database.RecurringTransactionRule
import it.danielebufarini.spesify.database.SpesifyDatabase
import it.danielebufarini.spesify.database.refreshExpenseSearchRows
import it.danielebufarini.spesify.database.refreshIncomeSearchRows
import it.danielebufarini.spesify.database.toStoredYearMonth
import kotlinx.datetime.TimeZone

class RecurringTransactionService(
    database: SpesifyDatabase,
    private val transactionRunner: DatabaseTransactionRunner,
    private val widgetRefreshCoordinator: WidgetRefreshCoordinator
) {
    private val expenseDao = database.expenseDao()
    private val incomeDao = database.incomeDao()
    private val recurringRuleDao = database.recurringTransactionRuleDao()
    private val searchIndexDao = database.searchIndexDao()

    suspend fun ensureRecurringTransactionsGeneratedThroughDefaultWindow() {
        val insertedCount = transactionRunner.runInTransaction {
            ensureRecurringTransactionsGeneratedThrough(
                targetYearMonth = recurringMaterializationTargetYearMonth()
            )
        }
        if (insertedCount > 0) {
            widgetRefreshCoordinator.requestRefresh()
        }
    }

    suspend fun updateRecurringIncomeSeries(
        anchorIncomeId: String,
        seriesId: String,
        amount: Long,
        date: Long,
        description: String?,
        categoryId: String?
    ) {
        transactionRunner.runInTransaction {
            val seriesItems = incomeDao.getRecurringIncomesBySeries(seriesId)
                .map { income ->
                    ExistingRecurringIncomeItem(
                        id = income.id,
                        date = income.date
                    )
                }
            val existingRule = recurringRuleDao.getRuleById(seriesId)
            val updatedIncomes = buildUpdatedRecurringIncomeSeries(
                existingItems = seriesItems,
                anchorItemId = anchorIncomeId,
                anchorDate = date,
                amount = amount,
                description = description,
                categoryId = categoryId,
                recurringSeriesId = seriesId
            )

            incomeDao.insertIncomes(
                updatedIncomes.map(PendingIncome::toEntity)
            )
            searchIndexDao.refreshIncomeSearchRows(updatedIncomes.map(PendingIncome::id))

            val generatedThroughYearMonth = maxOf(
                updatedIncomes.maxOfOrNull { it.date.toStoredYearMonth() } ?: date.toStoredYearMonth(),
                existingRule?.generatedThroughYearMonth ?: date.toStoredYearMonth()
            )
            recurringRuleDao.upsertRule(
                RecurringTransactionRule(
                    id = seriesId,
                    kind = RECURRING_TRANSACTION_KIND_INCOME,
                    amount = amount,
                    startDate = updatedIncomeRuleStartDate(
                        existingRule = existingRule,
                        existingItems = seriesItems,
                        anchorItemId = anchorIncomeId,
                        newAnchorDate = date
                    ),
                    frequency = RECURRING_TRANSACTION_FREQUENCY_MONTHLY,
                    intervalMonths = 1,
                    generatedThroughYearMonth = generatedThroughYearMonth,
                    categoryId = categoryId,
                    description = description.ifBlankToNull(),
                    isShared = 0L
                )
            )
            ensureRecurringTransactionsGeneratedThrough(
                targetYearMonth = recurringMaterializationTargetYearMonth()
            )
        }
        widgetRefreshCoordinator.requestRefresh()
    }

    suspend fun updateRecurringExpenseSeries(
        anchorExpenseId: String,
        seriesId: String,
        amount: Long,
        date: Long,
        categoryId: String,
        description: String?,
        isShared: Boolean
    ) {
        transactionRunner.runInTransaction {
            val seriesItems = expenseDao.getRecurringExpensesBySeries(seriesId)
                .map { expense ->
                    ExistingRecurringExpenseItem(
                        id = expense.id,
                        date = expense.date
                    )
                }
            val existingRule = recurringRuleDao.getRuleById(seriesId)
            val updatedExpenses = buildUpdatedRecurringExpenseSeries(
                existingItems = seriesItems,
                anchorItemId = anchorExpenseId,
                anchorDate = date,
                amount = amount,
                categoryId = categoryId,
                description = description,
                isShared = isShared,
                recurringSeriesId = seriesId
            )

            expenseDao.insertExpenses(
                updatedExpenses.map(PendingExpense::toEntity)
            )
            searchIndexDao.refreshExpenseSearchRows(updatedExpenses.map(PendingExpense::id))

            val generatedThroughYearMonth = maxOf(
                updatedExpenses.maxOfOrNull { it.date.toStoredYearMonth() } ?: date.toStoredYearMonth(),
                existingRule?.generatedThroughYearMonth ?: date.toStoredYearMonth()
            )
            recurringRuleDao.upsertRule(
                RecurringTransactionRule(
                    id = seriesId,
                    kind = RECURRING_TRANSACTION_KIND_EXPENSE,
                    amount = amount,
                    startDate = updatedExpenseRuleStartDate(
                        existingRule = existingRule,
                        existingItems = seriesItems,
                        anchorItemId = anchorExpenseId,
                        newAnchorDate = date
                    ),
                    frequency = RECURRING_TRANSACTION_FREQUENCY_MONTHLY,
                    intervalMonths = 1,
                    generatedThroughYearMonth = generatedThroughYearMonth,
                    categoryId = categoryId,
                    description = description.ifBlankToNull(),
                    isShared = if (isShared) 1L else 0L
                )
            )
            ensureRecurringTransactionsGeneratedThrough(
                targetYearMonth = recurringMaterializationTargetYearMonth()
            )
        }
        widgetRefreshCoordinator.requestRefresh()
    }

    private suspend fun ensureRecurringTransactionsGeneratedThrough(targetYearMonth: Int): Int {
        val dueRules = recurringRuleDao.getMonthlyRulesGeneratedBefore(targetYearMonth)
        var insertedCount = 0
        dueRules.forEach { rule ->
            insertedCount += when (rule.kind) {
                RECURRING_TRANSACTION_KIND_EXPENSE -> materializeExpenseRule(rule, targetYearMonth)
                RECURRING_TRANSACTION_KIND_INCOME -> materializeIncomeRule(rule, targetYearMonth)
                else -> 0
            }
        }
        return insertedCount
    }

    private suspend fun materializeExpenseRule(
        rule: RecurringTransactionRule,
        targetYearMonth: Int
    ): Int {
        if (rule.categoryId.isNullOrBlank()) {
            recurringRuleDao.updateGeneratedThroughYearMonth(rule.id, targetYearMonth)
            return 0
        }

        val expenses = buildPendingExpenseOccurrences(rule, targetYearMonth)
        if (expenses.isEmpty()) {
            recurringRuleDao.updateGeneratedThroughYearMonth(rule.id, targetYearMonth)
            return 0
        }

        expenseDao.insertExpenses(expenses.map(PendingExpense::toEntity))
        searchIndexDao.refreshExpenseSearchRows(expenses.map(PendingExpense::id))
        recurringRuleDao.updateGeneratedThroughYearMonth(rule.id, targetYearMonth)
        return expenses.size
    }

    private suspend fun materializeIncomeRule(
        rule: RecurringTransactionRule,
        targetYearMonth: Int
    ): Int {
        val incomes = buildPendingIncomeOccurrences(rule, targetYearMonth)
        if (incomes.isEmpty()) {
            recurringRuleDao.updateGeneratedThroughYearMonth(rule.id, targetYearMonth)
            return 0
        }

        incomeDao.insertIncomes(incomes.map(PendingIncome::toEntity))
        searchIndexDao.refreshIncomeSearchRows(incomes.map(PendingIncome::id))
        recurringRuleDao.updateGeneratedThroughYearMonth(rule.id, targetYearMonth)
        return incomes.size
    }

    private fun buildPendingExpenseOccurrences(
        rule: RecurringTransactionRule,
        targetYearMonth: Int,
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): List<PendingExpense> {
        val categoryId = rule.categoryId ?: return emptyList()
        return buildOccurrenceDatesAfterGeneratedWindow(
            rule = rule,
            targetYearMonth = targetYearMonth,
            timeZone = timeZone
        ).map { occurrenceDate ->
            PendingExpense(
                id = IdGenerator.newId("expense"),
                amount = rule.amount,
                date = occurrenceDate,
                categoryId = categoryId,
                description = rule.description,
                isShared = rule.isShared == 1L,
                recurringSeriesId = rule.id
            )
        }
    }

    private fun buildPendingIncomeOccurrences(
        rule: RecurringTransactionRule,
        targetYearMonth: Int,
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): List<PendingIncome> {
        return buildOccurrenceDatesAfterGeneratedWindow(
            rule = rule,
            targetYearMonth = targetYearMonth,
            timeZone = timeZone
        ).map { occurrenceDate ->
            PendingIncome(
                id = IdGenerator.newId("income"),
                amount = rule.amount,
                date = occurrenceDate,
                description = rule.description,
                recurringSeriesId = rule.id,
                categoryId = rule.categoryId
            )
        }
    }

    private fun buildOccurrenceDatesAfterGeneratedWindow(
        rule: RecurringTransactionRule,
        targetYearMonth: Int,
        timeZone: TimeZone
    ): List<Long> {
        if (rule.frequency != RECURRING_TRANSACTION_FREQUENCY_MONTHLY || rule.intervalMonths != 1) {
            return emptyList()
        }
        if (rule.startDate.toStoredYearMonth(timeZone) > targetYearMonth) {
            return emptyList()
        }

        val targetMonthStart = MonthKey(
            year = targetYearMonth / 100,
            month = targetYearMonth % 100
        ).toStartOfMonthMillis(timeZone)
        val maxOffset = monthDifference(
            firstDate = rule.startDate,
            secondDate = targetMonthStart,
            timeZone = timeZone
        ).coerceAtLeast(0)

        return (0..maxOffset)
            .asSequence()
            .map { offset -> monthlyOccurrenceDate(rule.startDate, offset, timeZone) }
            .filter { occurrenceDate ->
                val occurrenceYearMonth = occurrenceDate.toStoredYearMonth(timeZone)
                occurrenceYearMonth > rule.generatedThroughYearMonth &&
                    occurrenceYearMonth <= targetYearMonth
            }
            .toList()
    }
}

private fun updatedExpenseRuleStartDate(
    existingRule: RecurringTransactionRule?,
    existingItems: List<ExistingRecurringExpenseItem>,
    anchorItemId: String,
    newAnchorDate: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): Long {
    val anchorItem = existingItems.firstOrNull { it.id == anchorItemId }
        ?: return newAnchorDate
    val previousStartDate = existingRule?.startDate ?: existingItems.minOfOrNull { it.date } ?: anchorItem.date
    val anchorOffset = monthDifference(previousStartDate, anchorItem.date, timeZone)
    return monthlyOccurrenceDate(newAnchorDate, -anchorOffset, timeZone)
}

private fun updatedIncomeRuleStartDate(
    existingRule: RecurringTransactionRule?,
    existingItems: List<ExistingRecurringIncomeItem>,
    anchorItemId: String,
    newAnchorDate: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): Long {
    val anchorItem = existingItems.firstOrNull { it.id == anchorItemId }
        ?: return newAnchorDate
    val previousStartDate = existingRule?.startDate ?: existingItems.minOfOrNull { it.date } ?: anchorItem.date
    val anchorOffset = monthDifference(previousStartDate, anchorItem.date, timeZone)
    return monthlyOccurrenceDate(newAnchorDate, -anchorOffset, timeZone)
}

private fun String?.ifBlankToNull(): String? = this?.takeIf { it.isNotBlank() }
