package it.homebudget.app.database

import androidx.room.TypeConverter
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.toBigInteger

class BigIntegerTypeConverters {
    @TypeConverter
    fun fromDatabaseValue(value: String): BigInteger = value.toBigInteger()

    @TypeConverter
    fun toDatabaseValue(value: BigInteger): String = value.toString()
}
