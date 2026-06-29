package it.danielebufarini.spesify.appfunctions

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId

internal fun parseDateMillis(dateIso: String?, dateEpochMillis: Long?): Long? {
    dateEpochMillis?.takeIf { it > 0L }?.let { return it }
    val date = dateIso?.trim()?.takeIf(String::isNotEmpty) ?: return 0L
    return parseIsoDateMillis(date)
}

internal fun parsePeriodMillis(startDateIso: String, endDateIso: String): Pair<Long, Long>? {
    val startMillis = parseIsoDateMillis(startDateIso) ?: return null
    val endMillis = parseIsoDateMillis(endDateIso) ?: return null
    if (endMillis < startMillis) return null
    return startMillis to endMillis
}

private fun parseIsoDateMillis(dateIso: String): Long? {
    return runCatching {
        LocalDate.parse(dateIso.trim())
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }.getOrNull()
}

internal fun parseAmountMinorUnits(amount: String): Long? {
    val cleaned = amount
        .trim()
        .filter { it.isDigit() || it == '.' || it == ',' || it == '-' }

    if (cleaned.isBlank() || cleaned.contains('-')) return null

    val normalized = normalizeAmount(cleaned) ?: return null
    val decimalAmount = runCatching { BigDecimal(normalized) }.getOrNull() ?: return null
    if (decimalAmount <= BigDecimal.ZERO) return null

    val scale = decimalAmount.stripTrailingZeros().scale().coerceAtLeast(0)
    if (scale > 2) return null

    return runCatching {
        decimalAmount
            .movePointRight(2)
            .setScale(0, RoundingMode.UNNECESSARY)
            .longValueExact()
    }.getOrNull()
}

private fun normalizeAmount(cleaned: String): String? {
    val lastDot = cleaned.lastIndexOf('.')
    val lastComma = cleaned.lastIndexOf(',')
    val hasDot = lastDot >= 0
    val hasComma = lastComma >= 0

    if (!hasDot && !hasComma) {
        return cleaned.takeIf { it.any(Char::isDigit) }
    }

    if (hasDot && hasComma) {
        val decimalSeparator = if (lastDot > lastComma) '.' else ','
        val groupingSeparator = if (decimalSeparator == '.') ',' else '.'
        return cleaned
            .replace(groupingSeparator.toString(), "")
            .replace(decimalSeparator, '.')
            .takeIf(::hasValidDecimalShape)
    }

    val separator = if (hasDot) '.' else ','
    val parts = cleaned.split(separator)
    if (parts.size > 2) {
        return parts
            .takeIf { groups -> groups.first().isNotEmpty() && groups.drop(1).all { it.length == 3 } }
            ?.joinToString(separator = "")
    }

    val whole = parts.getOrElse(0) { "" }
    val fraction = parts.getOrElse(1) { "" }
    return when (fraction.length) {
        1, 2 -> "$whole.$fraction".takeIf(::hasValidDecimalShape)
        3 -> "$whole$fraction".takeIf { it.any(Char::isDigit) }
        else -> null
    }
}

private fun hasValidDecimalShape(value: String): Boolean {
    val parts = value.split('.')
    return parts.size <= 2 &&
        parts.any { it.isNotEmpty() } &&
        parts.all { part -> part.all(Char::isDigit) }
}
