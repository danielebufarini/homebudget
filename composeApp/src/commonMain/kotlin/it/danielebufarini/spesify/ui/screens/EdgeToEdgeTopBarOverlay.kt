package it.danielebufarini.spesify.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
internal fun EdgeToEdgeTopBarOverlay(
    modifier: Modifier = Modifier,
    topBar: @Composable (Modifier) -> Unit,
    content: @Composable BoxScope.(PaddingValues) -> Unit,
) {
    val density = LocalDensity.current
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    val topPadding = with(density) {
        if (topBarHeightPx == 0) 0.dp else topBarHeightPx.toDp()
    }

    Box(modifier = modifier.fillMaxSize()) {
        content(PaddingValues(top = topPadding))
        topBar(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    topBarHeightPx = coordinates.size.height
                },
        )
    }
}
