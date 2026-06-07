package it.danielebufarini.spesify.ui.screens.income

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator

class AddIncomeScreen(
    private val incomeId: String? = null,
    private val initialYear: Int? = null,
    private val initialMonth: Int? = null,
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
        AddIncomeRoute(
            incomeId = incomeId,
            initialYear = initialYear,
            initialMonth = initialMonth,
            showNavigationChrome = showNavigationChrome,
            onClose = onClose,
            useHostedFloatingChrome = useHostedFloatingChrome,
        )
    }
}
