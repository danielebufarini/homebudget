package it.homebudget.app.ui.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun platformColorScheme(
    useDarkTheme: Boolean,
    lightColors: ColorScheme,
    darkColors: ColorScheme
): ColorScheme {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return if (useDarkTheme) darkColors else lightColors
    }

    val context = LocalContext.current
    return if (useDarkTheme) {
        dynamicDarkColorScheme(context)
    } else {
        dynamicLightColorScheme(context)
    }
}
