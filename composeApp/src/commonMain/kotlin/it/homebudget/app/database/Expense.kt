package it.homebudget.app.database

import androidx.room.*
import com.ionspin.kotlin.bignum.integer.BigInteger

@Entity(
    tableName = "expense",
    indices = [Index("categoryId")],
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT
        )
    ]
)
data class Expense(
    @PrimaryKey
    val id: String,
    val amount: BigInteger,
    val date: Long,
    val categoryId: String,
    val description: String?,
    @ColumnInfo(defaultValue = "0")
    val isShared: Long = 0L,
    val recurringSeriesId: String? = null
)
