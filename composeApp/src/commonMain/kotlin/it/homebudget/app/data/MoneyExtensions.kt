package it.homebudget.app.data

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.BigInteger.Companion.ZERO

internal fun String.toAmountBigInteger(): BigInteger {
    return BigInteger.parseString(this)
}

internal fun Iterable<String>.sumAmounts(): BigInteger {
    return fold(ZERO) { acc, value ->
        acc + value.toAmountBigInteger()
    }
}