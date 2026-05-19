package it.homebudget.app.ui.screens

import it.homebudget.app.data.addAmountsExact
import it.homebudget.app.data.sumAmountOf
import it.homebudget.app.database.Category
import it.homebudget.app.database.Expense
import it.homebudget.app.database.Income

internal enum class ExpenseGroupingMode {
    ByCategory,
    ByDate
}

internal data class ExpenseSection(
    val key: String,
    val title: String,
    val expenses: List<Expense>,
    val totalAmount: Long
)

internal data class GroupedExpensesState(
    val visibleExpenses: List<Expense>,
    val sections: List<ExpenseSection>,
    val totalAmount: Long
)

internal data class IncomeSection(
    val key: String,
    val title: String,
    val incomes: List<Income>,
    val totalAmount: Long
)

internal data class GroupedIncomesState(
    val visibleIncomes: List<Income>,
    val sections: List<IncomeSection>,
    val totalAmount: Long
)

private data class ResolvedExpense(
    val expense: Expense,
    val categoryLabel: String
)

private data class ResolvedIncome(
    val income: Income,
    val categoryLabel: String
)

internal fun emptyGroupedExpensesState() = GroupedExpensesState(
    visibleExpenses = emptyList(),
    sections = emptyList(),
    totalAmount = 0L
)

internal fun emptyGroupedIncomesState() = GroupedIncomesState(
    visibleIncomes = emptyList(),
    sections = emptyList(),
    totalAmount = 0L
)

internal fun buildGroupedExpensesState(
    expenses: List<Expense>,
    categoriesById: Map<String, Category>,
    groupingMode: ExpenseGroupingMode,
    includeExpense: (Expense) -> Boolean,
    includeCategory: (String) -> Boolean,
    resolveCategoryName: (Category) -> String,
    unknownCategoryLabel: String,
    shortMonthNames: List<String>
): GroupedExpensesState {
    val visibleExpenses = ArrayList<ResolvedExpense>(expenses.size)
    var totalAmount = 0L

    expenses.forEach { expense ->
        val categoryLabel = categoriesById[expense.categoryId]
            ?.let(resolveCategoryName)
            ?: unknownCategoryLabel
        if (!includeExpense(expense) || !includeCategory(categoryLabel)) {
            return@forEach
        }

        visibleExpenses += ResolvedExpense(
            expense = expense,
            categoryLabel = categoryLabel
        )
        totalAmount = addAmountsExact(totalAmount, expense.amount)
    }

    val expenseComparator =
        compareByDescending<ResolvedExpense> { it.expense.date }
            .thenBy { it.categoryLabel }
            .thenBy { it.expense.description ?: "" }

    val sections = when (groupingMode) {
        ExpenseGroupingMode.ByCategory -> {
            visibleExpenses
                .groupBy(ResolvedExpense::categoryLabel)
                .entries
                .sortedBy { it.key }
                .map { (categoryLabel, groupEntries) ->
                    val sortedExpenses = groupEntries
                        .sortedWith(expenseComparator)
                        .map(ResolvedExpense::expense)
                    ExpenseSection(
                        key = "category:$categoryLabel",
                        title = categoryLabel,
                        expenses = sortedExpenses,
                        totalAmount = sortedExpenses.sumAmountOf(Expense::amount)
                    )
                }
        }
        ExpenseGroupingMode.ByDate -> {
            visibleExpenses
                .groupBy { entry -> epochMillisToLocalDate(entry.expense.date) }
                .entries
                .sortedByDescending { it.key }
                .map { (groupDate, groupEntries) ->
                    val sortedExpenses = groupEntries
                        .sortedWith(expenseComparator)
                        .map(ResolvedExpense::expense)
                    ExpenseSection(
                        key = "date:$groupDate",
                        title = formatExpenseDateGroupTitle(groupDate, shortMonthNames),
                        expenses = sortedExpenses,
                        totalAmount = sortedExpenses.sumAmountOf(Expense::amount)
                    )
                }
        }
    }

    return GroupedExpensesState(
        visibleExpenses = visibleExpenses.map(ResolvedExpense::expense),
        sections = sections,
        totalAmount = totalAmount
    )
}

internal fun buildGroupedIncomesState(
    incomes: List<Income>,
    categoriesById: Map<String, Category>,
    groupingMode: ExpenseGroupingMode,
    resolveCategoryName: (Category) -> String,
    unknownCategoryLabel: String,
    shortMonthNames: List<String>
): GroupedIncomesState {
    val visibleIncomes = ArrayList<ResolvedIncome>(incomes.size)
    var totalAmount = 0L

    incomes.forEach { income ->
        val categoryLabel = income.categoryId
            ?.let(categoriesById::get)
            ?.let(resolveCategoryName)
            ?: unknownCategoryLabel

        visibleIncomes += ResolvedIncome(
            income = income,
            categoryLabel = categoryLabel
        )
        totalAmount = addAmountsExact(totalAmount, income.amount)
    }

    val incomeComparator =
        compareByDescending<ResolvedIncome> { it.income.date }
            .thenBy { it.categoryLabel }
            .thenBy { it.income.description ?: "" }

    val sections = when (groupingMode) {
        ExpenseGroupingMode.ByCategory -> {
            visibleIncomes
                .groupBy(ResolvedIncome::categoryLabel)
                .entries
                .sortedBy { it.key }
                .map { (categoryLabel, groupEntries) ->
                    val sortedIncomes = groupEntries
                        .sortedWith(incomeComparator)
                        .map(ResolvedIncome::income)
                    IncomeSection(
                        key = "category:$categoryLabel",
                        title = categoryLabel,
                        incomes = sortedIncomes,
                        totalAmount = sortedIncomes.sumAmountOf(Income::amount)
                    )
                }
        }
        ExpenseGroupingMode.ByDate -> {
            visibleIncomes
                .groupBy { entry -> epochMillisToLocalDate(entry.income.date) }
                .entries
                .sortedByDescending { it.key }
                .map { (groupDate, groupEntries) ->
                    val sortedIncomes = groupEntries
                        .sortedWith(incomeComparator)
                        .map(ResolvedIncome::income)
                    IncomeSection(
                        key = "date:$groupDate",
                        title = formatExpenseDateGroupTitle(groupDate, shortMonthNames),
                        incomes = sortedIncomes,
                        totalAmount = sortedIncomes.sumAmountOf(Income::amount)
                    )
                }
        }
    }

    return GroupedIncomesState(
        visibleIncomes = visibleIncomes.map(ResolvedIncome::income),
        sections = sections,
        totalAmount = totalAmount
    )
}
