package it.danielebufarini.spesify.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import it.danielebufarini.spesify.data.csv.CsvImportStore
import it.danielebufarini.spesify.database.Category
import it.danielebufarini.spesify.database.Expense
import it.danielebufarini.spesify.database.Income
import it.danielebufarini.spesify.database.SpesifyDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal suspend fun TestScope.withFixture(block: suspend CategorySearchIndexFixture.() -> Unit) {
    val fixture = CategorySearchIndexFixture(this)
    try {
        fixture.block()
    } finally {
        fixture.close()
    }
}

internal class CategorySearchIndexFixture(testScope: TestScope) {
    val database: SpesifyDatabase = Room.inMemoryDatabaseBuilder<SpesifyDatabase>(
        context = ApplicationProvider.getApplicationContext<Context>()
    )
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
    val transactionRunner = DatabaseTransactionRunner(database)
    val widgetRefreshCoordinator = WidgetRefreshCoordinator(
        coroutineContext = testScope.backgroundScope.coroutineContext,
        debounceMillis = 1L,
        refreshAction = {}
    )
    val categoryRepository = CategoryRepository(database, transactionRunner)
    val expenseEntryRepository = ExpenseEntryRepository(
        database = database,
        transactionRunner = transactionRunner,
        widgetRefreshCoordinator = widgetRefreshCoordinator
    )
    val incomeRepository = IncomeRepository(
        database = database,
        transactionRunner = transactionRunner,
        widgetRefreshCoordinator = widgetRefreshCoordinator
    )
    val dataReplacementService = DataReplacementService(
        database = database,
        transactionRunner = transactionRunner,
        widgetRefreshCoordinator = widgetRefreshCoordinator
    )

    suspend fun insertCategory(id: String, name: String, type: String) {
        categoryRepository.insertCategory(
            id = id,
            name = name,
            icon = "category",
            color = "#123456",
            categoryType = type
        )
    }

    suspend fun insertExpense(
        id: String,
        categoryId: String,
        description: String,
        date: Long = testDate
    ) {
        expenseEntryRepository.insertExpenses(
            listOf(
                pendingExpense(
                    id = id,
                    categoryId = categoryId,
                    description = description,
                    date = date
                )
            )
        )
    }

    suspend fun insertIncome(
        id: String,
        categoryId: String,
        description: String,
        date: Long = testDate
    ) {
        incomeRepository.insertIncomes(
            listOf(
                pendingIncome(
                    id = id,
                    categoryId = categoryId,
                    description = description,
                    date = date
                )
            )
        )
    }

    suspend fun searchExpenseIds(query: String): Set<String> =
        expenseEntryRepository.searchExpenseCandidates(query, limit = 20)
            .first()
            .map(Expense::id)
            .toSet()

    suspend fun searchIncomeIds(query: String): Set<String> =
        incomeRepository.searchIncomeCandidates(query, limit = 20)
            .first()
            .map(Income::id)
            .toSet()

    suspend fun assertContainsExpense(query: String, id: String) {
        assertTrue(id in searchExpenseIds(query), "Expected expense $id for query '$query'.")
    }

    suspend fun assertDoesNotContainExpense(query: String, id: String) {
        assertFalse(id in searchExpenseIds(query), "Did not expect expense $id for query '$query'.")
    }

    suspend fun assertContainsIncome(query: String, id: String) {
        assertTrue(id in searchIncomeIds(query), "Expected income $id for query '$query'.")
    }

    suspend fun assertDoesNotContainIncome(query: String, id: String) {
        assertFalse(id in searchIncomeIds(query), "Did not expect income $id for query '$query'.")
    }

    fun close() {
        database.close()
    }
}

internal class RoomCsvImportStore(
    private val fixture: CategorySearchIndexFixture
) : CsvImportStore {
    override suspend fun seedStarterCategoriesIfEmpty() = Unit

    override suspend fun getAllCategoriesSnapshot(): List<Category> =
        fixture.categoryRepository.getAllCategoriesSnapshot()

    override suspend fun getAllExpensesSnapshot(): List<Expense> =
        fixture.expenseEntryRepository.getAllExpensesSnapshot()

    override suspend fun getAllIncomesSnapshot(): List<Income> =
        fixture.incomeRepository.getAllIncomesSnapshot()

    override suspend fun insertCategory(
        id: String,
        name: String,
        icon: String,
        color: String,
        categoryType: String,
        isArchived: Boolean,
        sortOrder: Long
    ) {
        fixture.categoryRepository.insertCategory(
            id = id,
            name = name,
            icon = icon,
            color = color,
            categoryType = categoryType,
            isArchived = isArchived,
            sortOrder = sortOrder
        )
    }

    override suspend fun insertExpenses(expenses: List<PendingExpense>) {
        fixture.expenseEntryRepository.insertExpenses(expenses)
    }

    override suspend fun insertIncomes(incomes: List<PendingIncome>) {
        fixture.incomeRepository.insertIncomes(incomes)
    }
}

internal val testDate: Long = LocalDate(2026, 1, 1)
    .atStartOfDayIn(TimeZone.of("UTC"))
    .toEpochMilliseconds()

internal fun pendingExpense(
    id: String,
    categoryId: String,
    description: String,
    date: Long = testDate
): PendingExpense = PendingExpense(
    id = id,
    amount = 1234L,
    date = date,
    categoryId = categoryId,
    description = description,
    isShared = false
)

internal fun pendingIncome(
    id: String,
    categoryId: String,
    description: String,
    date: Long = testDate
): PendingIncome = PendingIncome(
    id = id,
    amount = 5678L,
    date = date,
    description = description,
    categoryId = categoryId
)

internal fun millisForDate(year: Int, month: Int, day: Int): Long =
    LocalDate(year, month, day)
        .atStartOfDayIn(TimeZone.of("UTC"))
        .toEpochMilliseconds()
