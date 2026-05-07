package it.homebudget.app.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ionspin.kotlin.bignum.integer.BigInteger

@Entity(
    tableName = "income",
    indices = [
        Index(value = ["date"])
    ]
)
data class Income(
    @PrimaryKey
    val id: String,
    val amount: BigInteger,
    val date: Long,
    val description: String?,
    val recurringSeriesId: String? = null
)
