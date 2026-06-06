package it.danielebufarini.homebudget.data

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BudgetBackupValidationTest {

    @Test
    fun previewRestore_acceptsValidBackupWithoutChecksum() = runTest {
        val counters = parseBudgetBackup(validBackupJson())

        assertEquals(
            BudgetBackupCounters(
                categoriesCount = 2,
                expensesCount = 1,
                incomesCount = 1,
                createdAtEpochMillis = 1770000000000L,
                version = 4,
            ),
            counters,
        )
    }

    @Test
    fun previewRestore_rejectsExpenseAmountThatRestoreCannotParse() = runTest {
        val backup = validBackupJson(
            expenseAmount = "9223372036854775808",
        )

        assertFailsWith<IllegalArgumentException> {
            parseBudgetBackup(backup)
        }
    }

    @Test
    fun previewRestore_rejectsIncomeAmountThatRestoreCannotParse() = runTest {
        val backup = validBackupJson(
            incomeAmount = "not-a-number",
        )

        assertFailsWith<IllegalArgumentException> {
            parseBudgetBackup(backup)
        }
    }

    @Test
    fun previewRestore_rejectsBlankCategoryId() = runTest {
        val backup = validBackupJson(
            expenseCategoryId = "",
        )

        assertFailsWith<IllegalArgumentException> {
            parseBudgetBackup(backup)
        }
    }

    @Test
    fun previewRestore_rejectsUnsupportedCategoryType() = runTest {
        val backup = validBackupJson(
            expenseCategoryType = "transfer",
        )

        assertFailsWith<IllegalArgumentException> {
            parseBudgetBackup(backup)
        }
    }

    @Test
    fun previewRestore_rejectsDanglingTransactionCategoryReferences() = runTest {
        val backup = validBackupJson(
            expenseCategoryReference = "missing-category",
        )

        assertFailsWith<IllegalArgumentException> {
            parseBudgetBackup(backup)
        }
    }

    private fun validBackupJson(
        expenseCategoryId: String = "expense_food",
        incomeCategoryId: String = "income_salary",
        expenseCategoryType: String = "expense",
        incomeCategoryType: String = "income",
        expenseCategoryReference: String = expenseCategoryId,
        incomeCategoryReference: String = incomeCategoryId,
        expenseAmount: String = "1234",
        incomeAmount: String = "5678",
    ): String = """
        {
          "format": "homebudget_backup",
          "version": 4,
          "createdAtEpochMillis": 1770000000000,
          "categories": [
            {
              "id": "$expenseCategoryId",
              "name": "Food",
              "icon": "category",
              "color": "#6F45E9",
              "categoryType": "$expenseCategoryType",
              "isArchived": false,
              "sortOrder": 0
            },
            {
              "id": "$incomeCategoryId",
              "name": "Salary",
              "icon": "category",
              "color": "#2FA66A",
              "categoryType": "$incomeCategoryType",
              "isArchived": false,
              "sortOrder": 1
            }
          ],
          "expenses": [
            {
              "id": "expense_1",
              "amount": "$expenseAmount",
              "date": 1767225600000,
              "categoryId": "$expenseCategoryReference",
              "description": "Lunch",
              "isShared": false
            }
          ],
          "incomes": [
            {
              "id": "income_1",
              "amount": "$incomeAmount",
              "date": 1767312000000,
              "categoryId": "$incomeCategoryReference",
              "description": "Salary"
            }
          ]
        }
    """.trimIndent()
}
