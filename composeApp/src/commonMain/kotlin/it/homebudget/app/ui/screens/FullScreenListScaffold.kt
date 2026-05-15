package it.homebudget.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Keeps Android scrolling content edge-to-edge, like the categories management screen:
 * the list container fills the whole window and the app bar floats above it.
 *
 * iOS keeps the normal Scaffold behavior because the native shell already owns those
 * insets and the affected layout issue is Android-specific.
 */
@Composable
internal fun FullScreenAndroidListScaffold(
    topBar: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    if (rememberIsIosPlatform()) {
        Scaffold(topBar = topBar) { padding ->
            content(padding)
        }
        return
    }

    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val safeAreaPadding = WindowInsets.safeDrawing.asPaddingValues()
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    val topBarHeight = with(density) {
        if (topBarHeightPx == 0) {
            safeAreaPadding.calculateTopPadding() + 64.dp
        } else {
            topBarHeightPx.toDp()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        content(
            PaddingValues(
                start = safeAreaPadding.calculateStartPadding(layoutDirection),
                top = topBarHeight,
                end = safeAreaPadding.calculateEndPadding(layoutDirection),
                bottom = safeAreaPadding.calculateBottomPadding(),
            ),
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    topBarHeightPx = coordinates.size.height
                },
        ) {
            topBar()
        }
    }
}
