package it.danielebufarini.spesify.data

import kotlin.math.abs
import kotlin.math.roundToLong

private const val ZERO_AMOUNT = 0L
private const val MINOR_UNITS_PER_MAJOR = 100L
private const val MAX_EXPRESSION_LENGTH = 80

fun parseAmountInput(value: String): Long? {
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
    val cents = (whole + decimals).trimStart('0').ifEmpty { "0" }.toLongOrNull() ?: return null
    return if (negative) negateAmountExact(cents) else cents
}

fun evaluateAmountExpressionInput(value: String): Long? {
    val normalized = value.trim()
    if (normalized.isEmpty() || normalized.length > MAX_EXPRESSION_LENGTH) {
        return null
    }

    return runCatching {
        AmountExpressionParser(normalized).parseAmount()
    }.getOrNull()
}

fun parseSerializedAmount(value: String): Long? {
    return value.trim()
        .takeIf(String::isNotEmpty)
        ?.toLongOrNull()
}

fun formatAmount(amount: Long, currencySymbol: String): String {
    val (units, cents, sign) = amountComponents(amount)
    return "$currencySymbol $sign$units.$cents"
}

fun formatAmountInput(amount: Long): String {
    val (units, cents, sign) = amountComponents(amount)
    return "$sign$units.$cents"
}

private fun amountComponents(amount: Long): Triple<Long, String, String> {
    val negative = amount < ZERO_AMOUNT
    val absolute = if (negative) negateAmountExact(amount) else amount
    val units = absolute / MINOR_UNITS_PER_MAJOR
    val cents = (absolute % MINOR_UNITS_PER_MAJOR).toString().padStart(2, '0')
    val sign = if (negative) "-" else ""
    return Triple(units, cents, sign)
}

fun <T> Iterable<T>.sumAmountOf(selector: (T) -> Long): Long =
    fold(ZERO_AMOUNT) { acc, value -> addAmountsExact(acc, selector(value)) }

fun Long.toDisplayDouble(): Double = toDouble() / MINOR_UNITS_PER_MAJOR.toDouble()

fun averageAmount(total: Long, count: Int): Long {
    if (count <= 0) return ZERO_AMOUNT

    val divisor = count.toLong()
    val halfDivisor = divisor / 2L

    return if (total >= ZERO_AMOUNT) {
        addAmountsExact(total, halfDivisor) / divisor
    } else {
        subtractAmountsExact(total, halfDivisor) / divisor
    }
}

fun addAmountsExact(left: Long, right: Long): Long {
    val result = left + right
    if (((left xor result) and (right xor result)) < 0L) {
        error("Money amount overflowed Long minor-unit storage.")
    }
    return result
}

fun subtractAmountsExact(left: Long, right: Long): Long {
    if (right == Long.MIN_VALUE) {
        error("Money amount overflowed Long minor-unit storage.")
    }
    return addAmountsExact(left, -right)
}

private fun negateAmountExact(value: Long): Long {
    if (value == Long.MIN_VALUE) {
        error("Money amount overflowed Long minor-unit storage.")
    }
    return -value
}

private class AmountExpressionParser(
    private val expression: String
) {
    private var index = 0

    fun parseAmount(): Long? {
        val result = parseExpression() ?: return null
        skipWhitespace()
        if (index != expression.length || !result.isFinite()) {
            return null
        }

        val minorUnits = result * MINOR_UNITS_PER_MAJOR
        if (!minorUnits.isFinite() || abs(minorUnits) > Long.MAX_VALUE.toDouble()) {
            return null
        }
        return minorUnits.roundToLong()
    }

    private fun parseExpression(): Double? {
        var value = parseTerm() ?: return null
        while (true) {
            skipWhitespace()
            value = when {
                consume('+') -> value + (parseTerm() ?: return null)
                consume('-') -> value - (parseTerm() ?: return null)
                else -> return value
            }
            if (!value.isFinite()) return null
        }
    }

    private fun parseTerm(): Double? {
        var value = parseFactor() ?: return null
        while (true) {
            skipWhitespace()
            value = when {
                consume('*') || consume('×') -> value * (parseFactor() ?: return null)
                consume('/') || consume('÷') -> {
                    val divisor = parseFactor() ?: return null
                    if (abs(divisor) < 0.000000001) return null
                    value / divisor
                }
                else -> return value
            }
            if (!value.isFinite()) return null
        }
    }

    private fun parseFactor(): Double? {
        skipWhitespace()
        return when {
            consume('+') -> parseFactor()
            consume('-') -> parseFactor()?.let { -it }
            consume('(') -> {
                val value = parseExpression() ?: return null
                skipWhitespace()
                if (!consume(')')) return null
                value
            }
            else -> parseNumber()
        }
    }

    private fun parseNumber(): Double? {
        skipWhitespace()
        val start = index
        var sawDigit = false
        var sawSeparator = false

        while (index < expression.length) {
            val char = expression[index]
            when {
                char.isDigit() -> {
                    sawDigit = true
                    index += 1
                }
                char == '.' || char == ',' -> {
                    if (sawSeparator) return null
                    sawSeparator = true
                    index += 1
                }
                else -> break
            }
        }

        if (!sawDigit) return null
        return expression.substring(start, index)
            .replace(',', '.')
            .toDoubleOrNull()
            ?.takeIf(Double::isFinite)
    }

    private fun consume(char: Char): Boolean {
        if (index >= expression.length || expression[index] != char) {
            return false
        }
        index += 1
        return true
    }

    private fun skipWhitespace() {
        while (index < expression.length && expression[index].isWhitespace()) {
            index += 1
        }
    }
}
