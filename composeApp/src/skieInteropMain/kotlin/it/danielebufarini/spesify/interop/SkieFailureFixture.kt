package it.danielebufarini.spesify.interop

import it.danielebufarini.spesify.ui.screens.IosGroupedExpensesObserver
import it.danielebufarini.spesify.ui.screens.ExpenseGroupingMode
import kotlinx.coroutines.flow.flow

/** Compiled only by the isolated SKIE test variant. */
class SkieFailureFixture {
    val observer = IosGroupedExpensesObserver(ExpenseGroupingMode.ByCategory) {
        flow { error("Controlled repository read failure") }
    }
}
