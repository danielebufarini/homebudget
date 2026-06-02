package it.danielebufarini.homebudget.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "expense",
    indices = [
        Index(value = ["categoryId"]),
        Index(value = ["date"]),
        Index(value = ["yearMonth"]),
        Index(value = ["yearMonth", "categoryId"]),
        Index(value = ["yearMonth", "isShared"]),
        Index(value = ["localDate"]),
        Index(value = ["recurringSeriesId", "date"])
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
data class Expense(
    @PrimaryKey
    val id: String,
    val amount: Long,
    val date: Long,
    @ColumnInfo(defaultValue = "0")
    val yearMonth: Int = date.toStoredYearMonth(),
    @ColumnInfo(defaultValue = "0")
    val localDate: Int = date.toStoredLocalDate(),
    @ColumnInfo(defaultValue = "0")
    val dayOfMonth: Int = date.toStoredDayOfMonth(),
    val categoryId: String,
    val description: String?,
    @ColumnInfo(defaultValue = "0")
    val isShared: Long = 0L,
    val recurringSeriesId: String? = null
)
