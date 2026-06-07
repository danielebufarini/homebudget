package it.danielebufarini.spesify.ui.screens.platform

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen

actual fun platformSettingsScreen(): Screen = UnsupportedSettingsScreen

private object UnsupportedSettingsScreen : Screen {
    @Composable
    override fun Content() {
        Text("Settings")
    }
}
