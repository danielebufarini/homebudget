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
    return rememberSwipeToDeleteBoxState(
        itemId = itemId,
        onDeleteItem = onDeleteExpense
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun rememberSwipeToDeleteBoxState(
    itemId: String,
    onDeleteItem: (String) -> Unit
): SwipeToDismissBoxState {
    val currentOnDeleteItem by rememberUpdatedState(onDeleteItem)

    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { distance -> distance * 0.35f }
    )

    LaunchedEffect(itemId, dismissState.settledValue) {
        if (dismissState.settledValue == SwipeToDismissBoxValue.EndToStart) {
            currentOnDeleteItem(itemId)
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }

    return dismissState
}
