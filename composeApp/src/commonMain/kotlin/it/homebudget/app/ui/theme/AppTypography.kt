package it.homebudget.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

internal expect fun appFontFamily(): FontFamily

internal fun appTypography(): Typography {
    val fontFamily = appFontFamily()
    val base = Typography()

    return Typography(
        displayLarge = base.displayLarge.withFontFamily(fontFamily),
        displayMedium = base.displayMedium.withFontFamily(fontFamily),
        displaySmall = base.displaySmall.withFontFamily(fontFamily),
        headlineLarge = base.headlineLarge.withFontFamily(fontFamily),
        headlineMedium = base.headlineMedium.withFontFamily(fontFamily),
        headlineSmall = base.headlineSmall.withFontFamily(fontFamily),
        titleLarge = base.titleLarge.withFontFamily(fontFamily),
        titleMedium = base.titleMedium.withFontFamily(fontFamily),
        titleSmall = base.titleSmall.withFontFamily(fontFamily),
        bodyLarge = base.bodyLarge.withFontFamily(fontFamily),
        bodyMedium = base.bodyMedium.withFontFamily(fontFamily),
        bodySmall = base.bodySmall.withFontFamily(fontFamily),
        labelLarge = base.labelLarge.withFontFamily(fontFamily),
        labelMedium = base.labelMedium.withFontFamily(fontFamily),
        labelSmall = base.labelSmall.withFontFamily(fontFamily)
    )
}

private fun TextStyle.withFontFamily(fontFamily: FontFamily): TextStyle = copy(fontFamily = fontFamily)
