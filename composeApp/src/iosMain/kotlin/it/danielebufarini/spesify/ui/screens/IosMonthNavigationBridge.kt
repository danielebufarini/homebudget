package it.danielebufarini.spesify.ui.screens

import it.danielebufarini.spesify.data.MonthKey
import it.danielebufarini.spesify.data.minusMonths
import it.danielebufarini.spesify.data.plusMonths

class IosMonthCursor(
    val year: Int,
    val month: Int
)

class IosMonthNavigationBridge {
    fun previous(year: Int, month: Int): IosMonthCursor {
        return MonthKey(year = year, month = month)
            .minusMonths(1)
            .toIosMonthCursor()
    }

    fun next(year: Int, month: Int): IosMonthCursor {
        return MonthKey(year = year, month = month)
            .plusMonths(1)
            .toIosMonthCursor()
    }
}

private fun MonthKey.toIosMonthCursor(): IosMonthCursor {
    return IosMonthCursor(year = year, month = month)
}
