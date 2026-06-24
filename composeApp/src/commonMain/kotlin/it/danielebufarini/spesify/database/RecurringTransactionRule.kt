@file:OptIn(kotlin.experimental.ExperimentalObjCName::class)

package it.danielebufarini.spesify.database

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import kotlin.native.ObjCName

const val RECURRING_TRANSACTION_KIND_EXPENSE = "expense"
const val RECURRING_TRANSACTION_KIND_INCOME = "income"
const val RECURRING_TRANSACTION_FREQUENCY_MONTHLY = "monthly"

@Entity(
    tableName = "recurring_transaction_rule",
    indices = [
        Index(value = ["kind"]),
        Index(value = ["categoryId"]),
        Index(value = ["generatedThroughYearMonth"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT
        )
    ]
)
data class RecurringTransactionRule(
    @PrimaryKey
    val id: String,
    val kind: String,
    val amount: Long,
    val startDate: Long,
    @ColumnInfo(defaultValue = "'monthly'")
    val frequency: String = RECURRING_TRANSACTION_FREQUENCY_MONTHLY,
    @ColumnInfo(defaultValue = "1")
    val intervalMonths: Int = 1,
    @ColumnInfo(defaultValue = "0")
    val generatedThroughYearMonth: Int = startDate.toStoredYearMonth(),
    @ColumnInfo(defaultValue = "NULL")
    val categoryId: String? = null,
    @property:ObjCName(swiftName = "transactionDescription")
    val description: String? = null,
    @ColumnInfo(defaultValue = "0")
    val isShared: Long = 0L
)
