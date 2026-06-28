@file:OptIn(kotlin.experimental.ExperimentalObjCName::class)

package it.danielebufarini.spesify.database

import androidx.room3.Entity
import androidx.room3.Fts4
import androidx.room3.FtsOptions
import kotlin.native.ObjCName

@Entity(tableName = "expense_search_fts")
@Fts4(
    contentEntity = Any::class,
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
    prefix = [2, 3, 4],
    notIndexed = ["transactionId", "categoryId"]
)
data class ExpenseSearchFts(
    val transactionId: String,
    val categoryId: String?,
    val categoryName: String,
    @property:ObjCName(swiftName = "expenseDescription")
    val description: String,
    val amountText: String,
    val amountMinorText: String,
    val dateText: String,
    val localDateText: String,
    val yearMonthText: String
)

@Entity(tableName = "income_search_fts")
@Fts4(
    contentEntity = Any::class,
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
    prefix = [2, 3, 4],
    notIndexed = ["transactionId", "categoryId"]
)
data class IncomeSearchFts(
    val transactionId: String,
    val categoryId: String?,
    val categoryName: String,
    @property:ObjCName(swiftName = "incomeDescription")
    val description: String,
    val amountText: String,
    val amountMinorText: String,
    val dateText: String,
    val localDateText: String,
    val yearMonthText: String
)
