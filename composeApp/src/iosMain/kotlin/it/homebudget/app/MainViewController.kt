package it.homebudget.app

import androidx.compose.ui.window.ComposeUIViewController
import it.homebudget.app.di.initKoin

fun MainViewController() = ComposeUIViewController(
    configure = {
        initKoin()
    }
) { App() }
