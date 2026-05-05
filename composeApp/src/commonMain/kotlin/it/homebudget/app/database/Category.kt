package it.homebudget.app.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "category")
data class Category(
    @PrimaryKey
    val id: String,
    val name: String,
    val icon: String,
    @ColumnInfo(defaultValue = "0")
    val isCustom: Long = 0L
)
