package it.danielebufarini.spesify.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

const val CATEGORY_TYPE_EXPENSE = "expense"
const val CATEGORY_TYPE_INCOME = "income"
const val DEFAULT_CATEGORY_COLOR = "#6F45E9"

@Entity(tableName = "category")
data class Category(
    @PrimaryKey
    val id: String,
    val name: String,
    val icon: String,
    @ColumnInfo(defaultValue = "'$DEFAULT_CATEGORY_COLOR'")
    val color: String = DEFAULT_CATEGORY_COLOR,
    @ColumnInfo(defaultValue = "'$CATEGORY_TYPE_EXPENSE'")
    val categoryType: String = CATEGORY_TYPE_EXPENSE,
    @ColumnInfo(defaultValue = "0")
    val isArchived: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val sortOrder: Long = 0L
)
