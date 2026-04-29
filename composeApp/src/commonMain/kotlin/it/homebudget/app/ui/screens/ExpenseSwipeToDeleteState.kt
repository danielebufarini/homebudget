package it.homebudget.app.ui.screens

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun rememberExpenseSwipeToDeleteBoxState(
    itemId: String,
    onDeleteExpense: (String) -> Unit
): SwipeToDismissBoxState {
    val currentOnDeleteExpense by rememberUpdatedState(onDeleteExpense)

    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { distance -> distance * 0.35f }
    )

    LaunchedEffect(dismissState.settledValue) {
        if (dismissState.settledValue == SwipeToDismissBoxValue.EndToStart) {
            currentOnDeleteExpense(itemId)
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }

    return dismissState
}
