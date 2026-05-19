package it.homebudget.app.ui.screens

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

private val MonthSwipeNavigationThreshold = 72.dp
private const val MonthSwipeNavigationAxisDominance = 1.35f

fun Modifier.monthSwipeNavigation(
    enabled: Boolean = true,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
): Modifier = composed {
    val currentOnPreviousMonth by rememberUpdatedState(onPreviousMonth)
    val currentOnNextMonth by rememberUpdatedState(onNextMonth)
    val thresholdPx = with(LocalDensity.current) { MonthSwipeNavigationThreshold.toPx() }

    if (!enabled) {
        Modifier
    } else {
        pointerInput(thresholdPx) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val pointerId = down.id
                var totalDrag = Offset.Zero
                var handled = false
                var ownedByChildGesture = false

                do {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    val change = event.changes.firstOrNull { it.id == pointerId }
                        ?: event.changes.firstOrNull()
                        ?: break

                    if (change.isConsumed) {
                        ownedByChildGesture = true
                    }

                    if (!ownedByChildGesture && !handled) {
                        totalDrag += change.positionChange()
                        val horizontalDrag = abs(totalDrag.x)
                        val verticalDrag = abs(totalDrag.y)

                        if (
                            horizontalDrag >= thresholdPx &&
                            horizontalDrag > verticalDrag * MonthSwipeNavigationAxisDominance
                        ) {
                            handled = true
                            if (totalDrag.x < 0f) {
                                currentOnNextMonth()
                            } else {
                                currentOnPreviousMonth()
                            }
                            change.consume()
                        }
                    }
                } while (event.changes.any { it.pressed })
            }
        }
    }
}
