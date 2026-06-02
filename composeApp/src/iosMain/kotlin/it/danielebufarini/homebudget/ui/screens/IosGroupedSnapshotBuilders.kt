package it.danielebufarini.homebudget.ui.screens

import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.category
import homebudget.composeapp.generated.resources.currency_symbol
import homebudget.composeapp.generated.resources.expense
import homebudget.composeapp.generated.resources.income
import homebudget.composeapp.generated.resources.no_expenses_for_category_this_month
import homebudget.composeapp.generated.resources.no_expenses_for_day
import homebudget.composeapp.generated.resources.no_expenses_for_month
import homebudget.composeapp.generated.resources.no_income_for_month
import homebudget.composeapp.generated.resources.no_search_results
import homebudget.composeapp.generated.resources.no_shared_expenses_for_month
import homebudget.composeapp.generated.resources.shared_expense
import homebudget.composeapp.generated.resources.short_month_names
import homebudget.composeapp.generated.resources.unknown_category
import it.danielebufarini.homebudget.data.formatAmount
import it.danielebufarini.homebudget.data.sumAmountOf
import it.danielebufarini.homebudget.database.Category
import it.danielebufarini.homebudget.database.Expense
import it.danielebufarini.homebudget.database.Income
import it.danielebufarini.homebudget.localization.formatResourceArgs
import it.danielebufarini.homebudget.localization.loadCategoryNameResolver
import it.danielebufarini.homebudget.ui.screens.categories.normalizeCategoryIconKey
import it.danielebufarini.homebudget.ui.screens.expenses.epochMillisToLocalDate
import it.danielebufarini.homebudget.ui.screens.expenses.formatExpenseDate
import it.danielebufarini.homebudget.ui.screens.expenses.formatExpenseDateGroupTitle
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.getStringArray

internal data class IosPreparedGroup<T>(
    val name: String,
    val rows: List<T>
)

private enum class IosGroupingStrategy {
    Category,
    Date;

    val bridgeValue: String
        get() = when (this) {
            Category -> "category"
            Date -> "date"
        }

    companion object {
        fun fromBridgeValue(value: String): IosGroupingStrategy = when (value) {
            "date" -> Date
            else -> Category
        }
    }
}

internal fun buildSnapshotsCache(
    preparedExpenses: List<PreparedIosExpense>,
    key: GroupedExpensesCacheKey,
    localization: IosGroupedLocalization
): IosGroupedSnapshotsCache {
    val filteredExpenses = preparedExpenses.filter { expense ->
        expense.year == key.year &&
            expense.month == key.month &&
            (key.dayOfMonth == null || epochMillisToLocalDate(expense.dateMillis).day == key.dayOfMonth) &&
            includeExpense(expense, key.screenType) &&
            includeCategory(expense.categoryName, key.screenType, key.categoryName)
    }
    val totalAmountText = formatAmount(
        filteredExpenses.sumAmountOf(PreparedIosExpense::amount),
        localization.currencySymbol
    )
    val emptyStateText = emptyStateText(key.screenType, key.categoryName, localization)

    return IosGroupedSnapshotsCache(
        byCategory = IosGroupedExpensesSnapshot(
            totalAmountText = totalAmountText,
            emptyStateText = emptyStateText,
            sections = buildSections(
                groupedExpenses = filteredExpenses.groupExpensesByStrategy(IosGroupingStrategy.Category),
                groupingMode = "category",
                screenType = key.screenType,
                localization = localization
            )
        ),
        byDate = IosGroupedExpensesSnapshot(
            totalAmountText = totalAmountText,
            emptyStateText = emptyStateText,
            sections = buildSections(
                groupedExpenses = filteredExpenses.groupExpensesByStrategy(IosGroupingStrategy.Date),
                groupingMode = "date",
                screenType = key.screenType,
                localization = localization
            )
        )
    )
}

internal fun buildMonthlyIncomesSnapshot(
    incomes: List<Income>,
    categories: List<Category>,
    year: Int,
    month: Int,
    groupingMode: String,
    localization: IosGroupedLocalization
): IosMonthlyIncomesSnapshot {
    val categoriesById = categories.associateBy { it.id }
    val preparedIncomes = incomes
        .asSequence()
        .filter { income ->
            val localDate = epochMillisToLocalDate(income.date)
            localDate.year == year && localDate.month.ordinal + 1 == month
        }
        .map { income ->
            prepareIncome(
                income = income,
                categoriesById = categoriesById,
                localization = localization
            )
        }
        .toList()

    return buildPreparedIncomesSnapshot(
        preparedIncomes = preparedIncomes,
        groupingMode = groupingMode,
        emptyStateText = localization.noIncomeForMonth,
        localization = localization
    )
}

internal fun buildPreparedIncomesSnapshot(
    preparedIncomes: List<PreparedIosIncome>,
    groupingMode: String,
    emptyStateText: String,
    localization: IosGroupedLocalization
): IosMonthlyIncomesSnapshot {
    val strategy = IosGroupingStrategy.fromBridgeValue(groupingMode)
    val sections = preparedIncomes
        .groupIncomesByStrategy(strategy)
        .map { group ->
            val sortedIncomes = group.rows.sortedIncomesByTransactionFields()
            IosIncomeSection(
                id = "${strategy.bridgeValue}:${group.name}",
                title = group.name,
                categoryColorKey = if (strategy == IosGroupingStrategy.Date) null else sortedIncomes.firstOrNull()?.categoryId,
                categoryIconKey = if (strategy == IosGroupingStrategy.Date) null else sortedIncomes.firstOrNull()?.categoryIconKey,
                totalAmountText = formatAmount(
                    sortedIncomes.sumAmountOf(PreparedIosIncome::amount),
                    localization.currencySymbol
                ),
                rows = sortedIncomes.map { income ->
                    val incomeName = income.description?.ifBlank { localization.income } ?: localization.income
                    IosIncomeRow(
                        id = income.id,
                        title = if (strategy == IosGroupingStrategy.Date) income.categoryName else incomeName,
                        subtitleText = if (strategy == IosGroupingStrategy.Date) incomeName else income.dateText,
                        amountText = income.amountText,
                        categoryColorKey = income.categoryId,
                        categoryIconKey = income.categoryIconKey,
                        recurringSeriesId = income.recurringSeriesId
                    )
                }
            )
        }

    return IosMonthlyIncomesSnapshot(
        totalAmountText = formatAmount(
            preparedIncomes.sumAmountOf(PreparedIosIncome::amount),
            localization.currencySymbol
        ),
        emptyStateText = emptyStateText,
        sections = sections
    )
}

internal fun buildSections(
    groupedExpenses: List<IosPreparedGroup<PreparedIosExpense>>,
    groupingMode: String,
    screenType: String,
    localization: IosGroupedLocalization
): List<IosGroupedExpenseSection> {
    val strategy = IosGroupingStrategy.fromBridgeValue(groupingMode)
    return groupedExpenses.map { group ->
        val sortedExpenses = group.rows.sortedExpensesByTransactionFields()
        IosGroupedExpenseSection(
            id = group.name,
            title = group.name,
            categoryColorKey = if (strategy == IosGroupingStrategy.Date) null else sortedExpenses.firstOrNull()?.categoryId,
            categoryIconKey = if (strategy == IosGroupingStrategy.Date) null else sortedExpenses.firstOrNull()?.categoryIconKey,
            totalAmountText = formatAmount(
                sortedExpenses.sumAmountOf(PreparedIosExpense::amount),
                localization.currencySymbol
            ),
            rows = sortedExpenses.map { expense ->
                val expenseName = expense.description?.ifBlank { expenseFallbackTitle(screenType, localization) }
                    ?: expenseFallbackTitle(screenType, localization)
                IosGroupedExpenseRow(
                    id = expense.id,
                    title = if (strategy == IosGroupingStrategy.Date) expense.categoryName else expenseName,
                    subtitleText = if (strategy == IosGroupingStrategy.Date) expenseName else expense.dateText,
                    amountText = expense.amountText,
                    categoryColorKey = expense.categoryId,
                    categoryIconKey = expense.categoryIconKey,
                    recurringSeriesId = expense.recurringSeriesId
                )
            }
        )
    }
}

internal fun prepareExpense(
    expense: Expense,
    categoriesById: Map<String, Category>,
    localization: IosGroupedLocalization
): PreparedIosExpense {
    val localDate = epochMillisToLocalDate(expense.date)
    return PreparedIosExpense(
        id = expense.id,
        amount = expense.amount,
        amountText = formatAmount(expense.amount, localization.currencySymbol),
        categoryId = expense.categoryId,
        categoryName = categoriesById[expense.categoryId]
            ?.let { localization.resolveCategoryName(it.id, it.name) }
            ?: localization.unknownCategory,
        categoryIconKey = categoriesById[expense.categoryId]
            ?.icon
            ?.let(::normalizeCategoryIconKey),
        description = expense.description,
        recurringSeriesId = expense.recurringSeriesId,
        dateText = formatExpenseDate(expense.date),
        dateGroupTitleText = formatDateGroupTitle(expense.date, localization.shortMonthNames),
        dateMillis = expense.date,
        year = localDate.year,
        month = localDate.month.ordinal + 1,
        isShared = expense.isShared == 1L
    )
}

internal fun prepareIncome(
    income: Income,
    categoriesById: Map<String, Category>,
    localization: IosGroupedLocalization
): PreparedIosIncome {
    val localDate = epochMillisToLocalDate(income.date)
    val category = income.categoryId?.let(categoriesById::get)
    return PreparedIosIncome(
        id = income.id,
        amount = income.amount,
        amountText = formatAmount(income.amount, localization.currencySymbol),
        categoryId = category?.id,
        categoryName = category
            ?.let { localization.resolveCategoryName(it.id, it.name) }
            ?: localization.unknownCategory,
        categoryIconKey = category
            ?.icon
            ?.let(::normalizeCategoryIconKey),
        description = income.description,
        recurringSeriesId = income.recurringSeriesId,
        dateText = formatExpenseDate(income.date),
        dateGroupTitleText = formatDateGroupTitle(income.date, localization.shortMonthNames),
        dateMillis = income.date,
        year = localDate.year,
        month = localDate.month.ordinal + 1
    )
}

internal suspend fun loadIosGroupedLocalization(): IosGroupedLocalization {
    return IosGroupedLocalization(
        currencySymbol = getString(Res.string.currency_symbol),
        expense = getString(Res.string.expense),
        income = getString(Res.string.income),
        sharedExpense = getString(Res.string.shared_expense),
        category = getString(Res.string.category),
        noExpensesForDay = getString(Res.string.no_expenses_for_day),
        noExpensesForMonth = getString(Res.string.no_expenses_for_month),
        noSharedExpensesForMonth = getString(Res.string.no_shared_expenses_for_month),
        noIncomeForMonth = getString(Res.string.no_income_for_month),
        noSearchResults = getString(Res.string.no_search_results),
        unknownCategory = getString(Res.string.unknown_category),
        noExpensesForCategoryThisMonthTemplate = getString(Res.string.no_expenses_for_category_this_month),
        shortMonthNames = getStringArray(Res.array.short_month_names),
        resolveCategoryName = loadCategoryNameResolver()
    )
}

internal fun groupPreparedExpensesByMode(
    preparedExpenses: List<PreparedIosExpense>,
    groupingMode: String
): List<IosPreparedGroup<PreparedIosExpense>> =
    preparedExpenses.groupExpensesByStrategy(IosGroupingStrategy.fromBridgeValue(groupingMode))

private fun List<PreparedIosExpense>.groupExpensesByStrategy(
    strategy: IosGroupingStrategy
): List<IosPreparedGroup<PreparedIosExpense>> = groupBy {
    when (strategy) {
        IosGroupingStrategy.Category -> it.categoryName
        IosGroupingStrategy.Date -> it.dateGroupTitleText
    }
}
    .toPreparedGroups(strategy, PreparedIosExpense::dateMillis)

private fun List<PreparedIosIncome>.groupIncomesByStrategy(
    strategy: IosGroupingStrategy
): List<IosPreparedGroup<PreparedIosIncome>> = groupBy {
    when (strategy) {
        IosGroupingStrategy.Category -> it.categoryName
        IosGroupingStrategy.Date -> it.dateGroupTitleText
    }
}
    .toPreparedGroups(strategy, PreparedIosIncome::dateMillis)

private fun <T> Map<String, List<T>>.toPreparedGroups(
    strategy: IosGroupingStrategy,
    dateMillis: (T) -> Long
): List<IosPreparedGroup<T>> = toList()
    .sortedWith(
        when (strategy) {
            IosGroupingStrategy.Date -> compareByDescending<Pair<String, List<T>>> {
                it.second.maxOfOrNull(dateMillis) ?: Long.MIN_VALUE
            }
            IosGroupingStrategy.Category -> compareBy<Pair<String, List<T>>> { it.first }
        }
    )
    .map { (groupName, rows) ->
        IosPreparedGroup(name = groupName, rows = rows)
    }

private fun List<PreparedIosExpense>.sortedExpensesByTransactionFields(): List<PreparedIosExpense> =
    sortedWith(
        compareByDescending<PreparedIosExpense> { it.dateMillis }
            .thenBy { it.categoryName }
            .thenBy { it.description.orEmpty() }
    )

private fun List<PreparedIosIncome>.sortedIncomesByTransactionFields(): List<PreparedIosIncome> =
    sortedWith(
        compareByDescending<PreparedIosIncome> { it.dateMillis }
            .thenBy { it.categoryName }
            .thenBy { it.description.orEmpty() }
    )

private fun includeExpense(expense: PreparedIosExpense, screenType: String): Boolean = when (screenType) {
    "shared" -> expense.isShared
    else -> true
}

private fun includeCategory(groupName: String, screenType: String, categoryName: String?): Boolean = when (screenType) {
    "category" -> groupName == categoryName
    else -> true
}

private fun expenseFallbackTitle(screenType: String, localization: IosGroupedLocalization): String = when (screenType) {
    "shared" -> localization.sharedExpense
    else -> localization.expense
}

private fun emptyStateText(
    screenType: String,
    categoryName: String?,
    localization: IosGroupedLocalization
): String = when (screenType) {
    "shared" -> localization.noSharedExpensesForMonth
    "day" -> localization.noExpensesForDay
    "category" -> localization.noExpensesForCategoryThisMonthTemplate
        .formatResourceArgs(categoryName ?: localization.category)
    else -> localization.noExpensesForMonth
}

private fun formatDateGroupTitle(epochMillis: Long, shortMonthNames: List<String>): String {
    return formatExpenseDateGroupTitle(epochMillisToLocalDate(epochMillis), shortMonthNames)
}
