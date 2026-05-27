package it.homebudget.app.database

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions

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
    val description: String,
    val amountText: String,
    val amountMinorText: String,
    val dateText: String,
    val localDateText: String,
    val yearMonthText: String
)
