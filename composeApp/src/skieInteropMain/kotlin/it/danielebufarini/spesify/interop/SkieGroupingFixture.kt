package it.danielebufarini.spesify.interop

import it.danielebufarini.spesify.database.Category
import it.danielebufarini.spesify.database.Expense
import it.danielebufarini.spesify.ui.screens.*
import kotlin.time.Instant

class SkieGroupingFixture {
    fun snapshot(screenType: IosGroupedExpensesScreenType, groupingMode: ExpenseGroupingMode): IosGroupedExpensesSnapshot {
        val localization = IosGroupedLocalization(
            currencySymbol = "EUR", expense = "Expense", income = "Income", sharedExpense = "Shared",
            category = "Category", noExpensesForDay = "Day empty", noExpensesForMonth = "Month empty",
            noSharedExpensesForMonth = "Shared empty", noIncomeForMonth = "Income empty", noSearchResults = "Search empty",
            unknownCategory = "Unknown", noExpensesForCategoryThisMonthTemplate = "%s empty",
            shortMonthNames = List(12) { "M${it + 1}" }, resolveCategoryName = { _, name -> name }
        )
        val date = Instant.parse("2026-11-05T12:00:00Z").toEpochMilliseconds()
        val categories = mapOf("food" to Category(id = "food", name = "Food", icon = "restaurant"))
        val expenses = listOf(
            Expense(id = "a", amount = 1234L, date = date, categoryId = "food", description = "Lunch"),
            Expense(id = "b", amount = 200L, date = date - 86_400_000L, categoryId = "food", isShared = 1L, description = null)
        )
        val prepared = with(localization) { expenses.map { prepareExpense(it, categories) } }
        return buildSnapshotsCache(
            prepared,
            GroupedExpensesCacheKey(2026, 11, screenType, "Food", if (screenType == IosGroupedExpensesScreenType.Day) 5 else null),
            localization
        ).snapshotFor(groupingMode)
    }
}
