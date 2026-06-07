package it.danielebufarini.spesify.ui.screens.platform

import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable

@Composable
internal fun spesifyButtonColors(): ButtonColors {
    return ButtonDefaults.buttonColors()
}

@Composable
internal fun spesifyFilledTonalButtonColors(): ButtonColors {
    return ButtonDefaults.filledTonalButtonColors()
}

@Composable
internal fun spesifyOutlinedButtonColors(): ButtonColors {
    return ButtonDefaults.outlinedButtonColors()
}

@Composable
internal fun spesifyTextButtonColors(): ButtonColors {
    return ButtonDefaults.textButtonColors()
}
