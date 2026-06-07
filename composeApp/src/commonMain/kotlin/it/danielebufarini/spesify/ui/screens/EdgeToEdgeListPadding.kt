package it.danielebufarini.spesify.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun edgeToEdgeListContentPadding(
    scaffoldPadding: PaddingValues,
    horizontal: Dp = 16.dp,
    top: Dp = 16.dp,
    bottom: Dp = 16.dp
): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current

    return PaddingValues(
        start = scaffoldPadding.calculateStartPadding(layoutDirection) + horizontal,
        top = scaffoldPadding.calculateTopPadding() + top,
        end = scaffoldPadding.calculateEndPadding(layoutDirection) + horizontal,
        bottom = scaffoldPadding.calculateBottomPadding() + bottom
    )
}
