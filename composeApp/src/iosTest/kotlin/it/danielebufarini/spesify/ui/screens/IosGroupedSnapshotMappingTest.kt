package it.danielebufarini.spesify.ui.screens

import it.danielebufarini.spesify.database.Category
import it.danielebufarini.spesify.database.Expense
import it.danielebufarini.spesify.database.Income
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class IosGroupedSnapshotMappingTest {
    private val date = Instant.parse("2026-01-15T12:00:00Z").toEpochMilliseconds()
    private val categories = mapOf("food" to Category(id = "food", name = "Raw", icon = "restaurant"))

    @Test
    fun expenseMappingUsesOnlyItsLocalizationSnapshotAndPreservesTransactionFields() {
        val expense = expense("expense").copy(isShared = 1L, recurringSeriesId = "series")
        val first = with(localization("AA")) { prepareExpense(expense, categories) }
        val second = with(localization("BB")) { prepareExpense(expense, categories) }
        assertEquals("AA 12.34", first.amountText)
        assertEquals("BB 12.34", second.amountText)
        assertEquals("AA:food:Raw", first.categoryName)
        assertEquals("BB:food:Raw", second.categoryName)
        assertEquals("15 AA1 2026", first.dateGroupTitleText)
        assertEquals("15 BB1 2026", second.dateGroupTitleText)
        assertEquals(1234L, first.amount)
        assertEquals(date, first.dateMillis)
        assertEquals("series", first.recurringSeriesId)
        assertTrue(first.isShared)
        assertEquals("restaurant", first.categoryIconKey)
        val unknown = with(localization("AA")) { prepareExpense(expense.copy(categoryId = "absent"), categories) }
        assertEquals("AA unknown", unknown.categoryName)
        assertEquals("absent", unknown.categoryId)
        assertEquals(null, unknown.categoryIconKey)
    }

    @Test
    fun expenseSectionIdsTotalsAndDateOrderRemainStable() {
        val localization = localization("AA")
        val prepared = with(localization) {
            listOf(prepareExpense(expense("old", date - 86_400_000L), categories), prepareExpense(expense("new"), categories))
        }
        val cache = buildSnapshotsCache(prepared, GroupedExpensesCacheKey(2026, 1, "monthly", null), localization)
        assertEquals("AA 24.68", cache.byCategory.totalAmountText)
        assertEquals(listOf("AA:food:Raw"), cache.byCategory.sections.map { it.id })
        assertEquals(listOf("new", "old"), cache.byCategory.sections.single().rows.map { it.id })
        assertEquals(listOf("15 AA1 2026", "14 AA1 2026"), cache.byDate.sections.map { it.id })
    }

    @Test
    fun incomeMappingRetainsSectionPrefixesSortingFallbackAndMissingCategoryPolicy() {
        val localization = localization("AA")
        val incomes = listOf(
            Income(id = "second", amount = 1234L, date = date, categoryId = "food", description = "B"),
            Income(id = "first", amount = 1234L, date = date, categoryId = "food", description = "A")
        )
        val categorySnapshot = buildMonthlyIncomesSnapshot(incomes, categories.values.toList(), 2026, 1, "category", localization)
        val dateSnapshot = buildMonthlyIncomesSnapshot(incomes, categories.values.toList(), 2026, 1, "date", localization)
        assertEquals("AA 24.68", categorySnapshot.totalAmountText)
        assertEquals("category:AA:food:Raw", categorySnapshot.sections.single().id)
        assertEquals("date:15 AA1 2026", dateSnapshot.sections.single().id)
        assertEquals(listOf("first", "second"), categorySnapshot.sections.single().rows.map { it.id })
        val unknown = with(localization) { prepareIncome(incomes.first().copy(categoryId = "absent", description = null), categories) }
        assertEquals(null, unknown.categoryId)
        assertEquals("AA unknown", unknown.categoryName)
        assertEquals(null, unknown.categoryIconKey)
        val snapshot = buildPreparedIncomesSnapshot(listOf(unknown), "category", "empty", localization)
        assertEquals("AA income", snapshot.sections.single().rows.single().title)
    }

    @Test
    fun recurringMappingKeepsAscendingDateThenIdAndLocalizedSharedMetadata() {
        val localization = IosRecurringLocalization(
            currencySymbol = "RR", monthly = "monthly", next = "next", noRecurringExpenses = "empty",
            noRecurringExpensesDescription = "empty details", perMonth = "per month", recurringExpenses = "recurring",
            shared = "shared", totalRecurring = "total", unknownCategory = "unknown",
            shortMonthNames = List(12) { "RR${it + 1}" }, resolveCategoryName = { id, name -> "RR:$id:$name" }
        )
        val prepared = with(localization) {
            listOf(
                prepareRecurringExpense(expense("b").copy(isShared = 1L), categories),
                prepareRecurringExpense(expense("c", date - 86_400_000L), categories),
                prepareRecurringExpense(expense("a"), categories)
            )
        }
        val snapshot = buildRecurringExpensesSnapshot(prepared, localization)
        assertEquals(listOf("c", "a", "b"), snapshot.rows.map { it.id })
        assertEquals("RR 37.02 / per month", snapshot.totalAmountText)
        assertTrue(snapshot.rows.last().subtitleText.endsWith(" · shared"))
        assertFalse(snapshot.rows.first().subtitleText.contains("shared"))
        assertEquals("RR:food:Raw", snapshot.rows.first().title)
    }

    private fun expense(id: String, dateMillis: Long = date) = Expense(
        id = id, amount = 1234L, date = dateMillis, categoryId = "food", description = null
    )

    private fun localization(tag: String) = IosGroupedLocalization(
        currencySymbol = tag, expense = "$tag expense", income = "$tag income", sharedExpense = "$tag shared",
        category = "$tag category", noExpensesForDay = "empty", noExpensesForMonth = "empty",
        noSharedExpensesForMonth = "empty", noIncomeForMonth = "empty", noSearchResults = "empty",
        unknownCategory = "$tag unknown", noExpensesForCategoryThisMonthTemplate = "empty %s",
        shortMonthNames = List(12) { "$tag${it + 1}" }, resolveCategoryName = { id, name -> "$tag:$id:$name" }
    )
}
