package it.homebudget.app.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "income",
    indices = [
        Index(value = ["categoryId"]),
        Index(value = ["date"]),
        Index(value = ["yearMonth"]),
        Index(value = ["yearMonth", "categoryId"]),
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
data class Income(
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
    val description: String?,
    val recurringSeriesId: String? = null,
    @ColumnInfo(defaultValue = "NULL")
    val categoryId: String? = null
)
