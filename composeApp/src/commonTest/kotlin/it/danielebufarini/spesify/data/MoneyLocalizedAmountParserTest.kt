package it.danielebufarini.spesify.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoneyLocalizedAmountParserTest {
    @Test
    fun parsesCommonItalianAndEuropeanAmountsAsMinorUnits() {
        assertEquals(1_234L, parsePositiveLocalizedAmountMinorOrNull("12,34"))
        assertEquals(1_234L, parsePositiveLocalizedAmountMinorOrNull("12.34"))
        assertEquals(123_456L, parsePositiveLocalizedAmountMinorOrNull("1.234,56"))
        assertEquals(123_456L, parsePositiveLocalizedAmountMinorOrNull("1234,56"))
        assertEquals(1_000L, parsePositiveLocalizedAmountMinorOrNull("10"))
        assertEquals(99L, parsePositiveLocalizedAmountMinorOrNull("0,99"))
    }

    @Test
    fun rejectsInvalidZeroNegativeAndUnsupportedFormats() {
        assertNull(parsePositiveLocalizedAmountMinorOrNull("not an amount"))
        assertNull(parsePositiveLocalizedAmountMinorOrNull("0"))
        assertNull(parsePositiveLocalizedAmountMinorOrNull("0,00"))
        assertNull(parsePositiveLocalizedAmountMinorOrNull("-12,34"))
        assertNull(parsePositiveLocalizedAmountMinorOrNull("12,345"))
    }
}
