package it.danielebufarini.spesify.ui.screens.transactions

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntSize

@Composable
internal fun Modifier.keepFocusedFieldVisible(): Modifier {
    val requester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val keyboardInset = WindowInsets.ime.getBottom(LocalDensity.current)
    val windowSize = LocalWindowInfo.current.containerSize
    LaunchedEffect(focused, keyboardInset, windowSize, size) {
        if (focused) requester.bringIntoView()
    }
    return bringIntoViewRequester(requester)
        .onFocusChanged { focused = it.hasFocus }
        .onSizeChanged { size = it }
}
