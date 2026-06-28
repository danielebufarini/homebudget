@file:OptIn(kotlin.experimental.ExperimentalObjCName::class)

package it.danielebufarini.spesify.database

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import kotlin.native.ObjCName

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
    @property:ObjCName(swiftName = "incomeDescription")
    val description: String?,
    val recurringSeriesId: String? = null,
    @ColumnInfo(defaultValue = "NULL")
    val categoryId: String? = null
)
