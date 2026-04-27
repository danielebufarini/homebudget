package it.homebudget.app.ui.screens

import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
internal fun androidAccentButtonContainerColor() = MaterialTheme.colorScheme.primaryContainer

@Composable
internal fun androidAccentButtonContentColor() = MaterialTheme.colorScheme.onPrimaryContainer

@Composable
internal fun homeBudgetButtonColors(): ButtonColors {
    return if (rememberIsIosPlatform()) {
        ButtonDefaults.buttonColors()
    } else {
        ButtonDefaults.buttonColors(
            containerColor = androidAccentButtonContainerColor(),
            contentColor = androidAccentButtonContentColor()
        )
    }
}

@Composable
internal fun homeBudgetFilledTonalButtonColors(): ButtonColors {
    return if (rememberIsIosPlatform()) {
        ButtonDefaults.filledTonalButtonColors()
    } else {
        ButtonDefaults.filledTonalButtonColors(
            containerColor = androidAccentButtonContainerColor(),
            contentColor = androidAccentButtonContentColor()
        )
    }
}

@Composable
internal fun homeBudgetOutlinedButtonColors(): ButtonColors {
    return if (rememberIsIosPlatform()) {
        ButtonDefaults.outlinedButtonColors()
    } else {
        ButtonDefaults.outlinedButtonColors(
            containerColor = androidAccentButtonContainerColor(),
            contentColor = androidAccentButtonContentColor()
        )
    }
}

@Composable
internal fun homeBudgetTextButtonColors(): ButtonColors {
    return if (rememberIsIosPlatform()) {
        ButtonDefaults.textButtonColors()
    } else {
        ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.primary
        )
    }
}
