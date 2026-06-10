package it.danielebufarini.spesify.ui.screens.expenses

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import it.danielebufarini.spesify.ui.screens.transactions.ExpenseEditorPrefill

class AddExpenseScreen(
    private val expenseId: String? = null,
    private val readOnly: Boolean = false,
    private val initialPrefill: ExpenseEditorPrefill? = null,
) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        RouteContent(
            showNavigationChrome = true,
            onClose = { navigator?.pop() },
        )
    }

    @Composable
    fun RouteContent(
        showNavigationChrome: Boolean,
        onClose: () -> Unit,
        useHostedFloatingChrome: Boolean = false,
    ) {
        AddExpenseRoute(
            expenseId = expenseId,
            readOnly = readOnly,
            showNavigationChrome = showNavigationChrome,
            onClose = onClose,
            useHostedFloatingChrome = useHostedFloatingChrome,
            initialPrefill = initialPrefill,
        )
    }
}
