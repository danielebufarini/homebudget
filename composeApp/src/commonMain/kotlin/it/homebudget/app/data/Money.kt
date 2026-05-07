package it.homebudget.app.data

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.BigInteger.Companion.ZERO
import com.ionspin.kotlin.bignum.integer.toBigInteger

private val ZERO = ZERO
private val ONE_HUNDRED = 100.toBigInteger()

fun parseAmountInput(value: String): BigInteger? {
    val normalized = value.trim().replace(',', '.')
    if (normalized.isEmpty()) {
        return null
    }

    val negative = normalized.startsWith("-")
    val unsigned = if (negative) normalized.drop(1) else normalized
    val parts = unsigned.split('.')
    if (parts.size > 2) {
        return null
    }

    val whole = parts[0].ifEmpty { "0" }
    if (!whole.all(Char::isDigit)) {
        return null
    }

    val decimalsRaw = parts.getOrNull(1).orEmpty()
    if (!decimalsRaw.all(Char::isDigit) || decimalsRaw.length > 2) {
        return null
    }

    val decimals = decimalsRaw.padEnd(2, '0')
    val cents = (whole + decimals).trimStart('0').ifEmpty { "0" }.toBigInteger()
    return if (negative) -cents else cents
}

fun formatAmount(amount: BigInteger, currencySymbol: String): String {
    val (units, cents, sign) = amountComponents(amount)
    return "$currencySymbol $sign$units.$cents"
}

fun formatAmountInput(amount: BigInteger): String {
    val (units, cents, sign) = amountComponents(amount)
    return "$sign$units.$cents"
}

private fun amountComponents(amount: BigInteger): Triple<BigInteger, String, String> {
    val negative = amount < ZERO
    val absolute = if (negative) -amount else amount
    val units = absolute / ONE_HUNDRED
    val cents = (absolute % ONE_HUNDRED).toString().padStart(2, '0')
    val sign = if (negative) "-" else ""
    return Triple(units, cents, sign)
}

fun <T> Iterable<T>.sumBigIntegerOf(selector: (T) -> BigInteger): BigInteger =
    fold(ZERO) { acc, value -> acc + selector(value) }

fun BigInteger.toDisplayDouble(): Double = toString().toDouble() / 100.0

fun averageAmount(total: BigInteger, count: Int): BigInteger {
    if (count <= 0) return ZERO

    val divisor = count.toBigInteger()
    val halfDivisor = divisor / 2.toBigInteger()

    return if (total >= ZERO) {
        (total + halfDivisor) / divisor
    } else {
        (total - halfDivisor) / divisor
    }
}
