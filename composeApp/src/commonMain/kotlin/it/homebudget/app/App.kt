package it.homebudget.app

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
import it.homebudget.app.localization.ProvideAppStrings
import it.homebudget.app.ui.screens.DashboardScreen
import it.homebudget.app.ui.theme.AppTheme

@Composable
fun App() {
    ProvideAppStrings {
        AppTheme {
            Navigator(DashboardScreen()) {
                CurrentScreen()
            }
        }
    }
}
