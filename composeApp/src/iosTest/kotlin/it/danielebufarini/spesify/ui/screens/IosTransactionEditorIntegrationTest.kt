package it.danielebufarini.spesify.ui.screens

import it.danielebufarini.spesify.data.*
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosTransactionEditorIntegrationTest {
    private lateinit var writes: EditorRecordingRepository
    @BeforeTest
    fun startContainer() {
        stopKoin()
        writes = EditorRecordingRepository()
        startKoin { modules(module { single { TransactionEditorService(writes) } }) }
    }
    @AfterTest
    fun stopContainer() { stopKoin() }

    @Test
    fun nativeExpenseEditor_matchesSharedInstallmentsWithoutTrimmingNotes() = runTest {
        val result = IosNativeTransactionEditorController().saveExpense("10.01", date, "food", "  note  ", true, false, 3)
        assertTrue(result.isSuccess)
        assertEquals(listOf(334L, 334L, 333L), writes.expenses.map { it.amount })
        assertEquals(date, writes.expenses.first().date)
        assertTrue(writes.expenses.all { it.description == "  note  " && it.categoryId == "food" && it.isShared })
    }

    @Test
    fun nativeIncomeEditor_keepsOptionalCategoryAndUntrimmedDescription() = runTest {
        val result = IosNativeTransactionEditorController().saveIncome("123.45", date, " ", "  salary  ", false)
        assertTrue(result.isSuccess)
        val row = writes.incomes.single()
        assertEquals(12345L, row.amount)
        assertEquals(null, row.categoryId)
        assertEquals("  salary  ", row.description)
        assertTrue(row.id.startsWith("income-"))
    }

    @Test
    fun nativeValidation_doesNotWriteInvalidAmountsOrCategories() = runTest {
        val controller = IosNativeTransactionEditorController()
        assertEquals("Enter a valid amount", controller.saveExpense("invalid", date, "food", "", false, false, 1).errorKey)
        assertEquals("Select Category", controller.saveExpense("12", date, "", "", false, false, 1).errorKey)
        assertTrue(writes.expenses.isEmpty())
    }
}

private val date get() = LocalDate(2026, 1, 31).atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
