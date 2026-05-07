package it.homebudget.app.database

import androidx.room.TypeConverter
import com.ionspin.kotlin.bignum.integer.BigInteger

class BigIntegerTypeConverters {

    @TypeConverter
    fun fromDatabaseValue(value: String): BigInteger {
        return BigInteger.parseString(value)
    }

    @TypeConverter
    fun toDatabaseValue(value: BigInteger): String {
        return value.toString()
    }
}
