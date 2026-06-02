package it.danielebufarini.homebudget.ui.screens.expenses

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator

class AddExpenseScreen(
    private val expenseId: String? = null,
    private val readOnly: Boolean = false,
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
        )
    }
}
