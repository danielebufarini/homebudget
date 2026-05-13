package it.homebudget.app.ui.screens

import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable

@Composable
internal fun homeBudgetButtonColors(): ButtonColors {
    return ButtonDefaults.buttonColors()
}

@Composable
internal fun homeBudgetFilledTonalButtonColors(): ButtonColors {
    return ButtonDefaults.filledTonalButtonColors()
}

@Composable
internal fun homeBudgetOutlinedButtonColors(): ButtonColors {
    return ButtonDefaults.outlinedButtonColors()
}

@Composable
internal fun homeBudgetTextButtonColors(): ButtonColors {
    return ButtonDefaults.textButtonColors()
}
