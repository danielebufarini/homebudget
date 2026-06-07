package it.danielebufarini.homebudget.ui.screens.transactions

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalViewConfiguration
import it.danielebufarini.homebudget.ui.screens.dismissPlatformKeyboard

@Composable
internal fun rememberKeyboardDismissAction(): () -> Unit {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    return remember(focusManager, keyboardController) {
        {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            dismissPlatformKeyboard()
        }
    }
}

internal fun Modifier.dismissKeyboardOnOutsideTap(
    onDismissKeyboard: () -> Unit
): Modifier = composed {
    val currentOnDismissKeyboard by rememberUpdatedState(onDismissKeyboard)
    val touchSlop = LocalViewConfiguration.current.touchSlop

    pointerInput(touchSlop) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val pointerId = down.id
            var ownedByChildGesture = down.isConsumed
            var totalPositionChange = Offset.Zero

            do {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == pointerId }
                    ?: event.changes.firstOrNull()
                    ?: break

                if (change.isConsumed) {
                    ownedByChildGesture = true
                }

                totalPositionChange += change.positionChange()

                if (!change.pressed) {
                    if (!ownedByChildGesture && totalPositionChange.getDistance() < touchSlop) {
                        currentOnDismissKeyboard()
                    }
                    break
                }
            } while (event.changes.any { it.pressed })
        }
    }
}
