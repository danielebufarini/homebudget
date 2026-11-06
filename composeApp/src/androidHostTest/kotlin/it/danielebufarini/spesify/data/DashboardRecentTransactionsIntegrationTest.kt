package it.danielebufarini.spesify.data

import it.danielebufarini.spesify.database.CATEGORY_TYPE_EXPENSE
import it.danielebufarini.spesify.database.CATEGORY_TYPE_INCOME
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class DashboardRecentTransactionsIntegrationTest {
    private val zone = TimeZone.of("Pacific/Auckland")
    private val tomorrow = LocalDate(2026, 11, 7).atStartOfDayIn(zone).toEpochMilliseconds()
    private val clock = object : Clock {
        override fun now() = Instant.parse("2026-11-06T00:00:00Z")
    }

    @Test
    fun cutoffPrecedesLimitAndKeepsTodaysManualAndRecurringExpensesAndFutureIncome() = runTest {
        withFixture {
            insertCategory("expense", "Expenses", CATEGORY_TYPE_EXPENSE)
            insertCategory("income", "Income", CATEGORY_TYPE_INCOME)
            val oldDate = LocalDate(2026, 11, 5).atStartOfDayIn(zone).toEpochMilliseconds()
            val olderExpenses = List(55) { index ->
                pendingExpense("old_$index", "expense", "Previous", oldDate - index * 60_000L)
            }
            val todaysExpenses = listOf(
                pendingExpense("today_manual", "expense", "Today", tomorrow - 1L),
                pendingExpense("today_recurring", "expense", "Recurring today", tomorrow - 2L)
                    .copy(recurringSeriesId = "today_series")
            )
            val futureExpenses = List(60) { index ->
                pendingExpense("future_$index", "expense", "Future", tomorrow + index * 60_000L)
                    .copy(recurringSeriesId = if (index % 2 == 0) "future_series_$index" else null)
            }
            expenseEntryRepository.insertExpenses(olderExpenses + todaysExpenses + futureExpenses)
            insertIncome("future_income", "income", "Future income", tomorrow + 86_400_000L)
            insertIncome("next_month_income", "income", "Next month", LocalDate(2026, 12, 2).atStartOfDayIn(zone).toEpochMilliseconds())
            val adapter = DashboardReadRepositoryAdapter(DashboardRepository(database, clock) { zone })
            val expected = listOf("future_income", "today_manual", "today_recurring") + olderExpenses.map { it.id }

            for (limit in listOf(15, 50)) {
                val filtered = adapter.getDashboardRecentTransactions(limit, tomorrow).first()
                assertEquals(expected.take(limit), filtered.map { it.id })
                assertEquals(DashboardRecentTransactionType.Income, filtered.first().type)
            }
            val unfiltered = adapter.getDashboardRecentTransactions(50).first()
            assertEquals(50, unfiltered.size)
            assertTrue(unfiltered.drop(1).all { it.id.startsWith("future_") })
            assertEquals("future_income", unfiltered.first().id)
            assertEquals(unfiltered, adapter.getDashboardRecentTransactions(50, null).first())

            val summary = adapter.getDashboardMonthSummary(2026, 11).first()
            assertEquals(117, summary.expenseCount)
            assertEquals(117 * 1234L, summary.totalAmount)
            assertEquals(5678L, summary.incomeAmount)
        }
    }

    @Test
    fun cutoffCanProduceAnEmptyListAndFacadeForwardsIt() = runTest {
        withFixture {
            insertCategory("expense", "Expenses", CATEGORY_TYPE_EXPENSE)
            insertExpense("future", "expense", "Future", tomorrow)
            val facade = ExpenseRepository(
                categoryRepository, expenseEntryRepository, incomeRepository,
                DashboardRepository(database, clock) { zone },
                RecurringTransactionService(database, transactionRunner, widgetRefreshCoordinator),
                dataReplacementService
            )
            assertTrue(facade.getDashboardRecentTransactions(expensesBeforeExclusiveMillis = tomorrow).first().isEmpty())
            assertEquals(listOf("future"), facade.getDashboardRecentTransactions().first().map { it.id })
        }
    }
}
