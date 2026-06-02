package it.danielebufarini.homebudget.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.Flow

internal data class FlowLoadState<T>(
    val value: T,
    val isLoading: Boolean
)

@Composable
internal fun <T> Flow<T>.collectAsFlowLoadState(
    initialValue: T,
    resetKey: Any? = this
): FlowLoadState<T> {
    var value by remember { mutableStateOf(initialValue) }
    var isLoading by remember { mutableStateOf(true) }
    var previousResetKey by remember { mutableStateOf<Any?>(null) }

    LaunchedEffect(this, resetKey) {
        if (previousResetKey != resetKey) {
            value = initialValue
            isLoading = true
            previousResetKey = resetKey
        }

        collect { nextValue ->
            value = nextValue
            isLoading = false
        }
    }

    return FlowLoadState(
        value = value,
        isLoading = isLoading
    )
}
