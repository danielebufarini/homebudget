package it.homebudget.app.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "income",
    indices = [
        Index(value = ["date"]),
        Index(value = ["recurringSeriesId", "date"])
    ]
)
data class Income(
    @PrimaryKey
    val id: String,
    val amount: Long,
    val date: Long,
    val description: String?,
    val recurringSeriesId: String? = null
)
