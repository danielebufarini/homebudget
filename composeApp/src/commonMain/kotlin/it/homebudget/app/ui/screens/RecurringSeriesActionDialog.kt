package it.homebudget.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun RecurringSeriesActionDialog(
    title: String,
    message: String,
    onThisInstanceOnly: () -> Unit,
    onWholeSeries: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = onWholeSeries,
                colors = homeBudgetTextButtonColors()
            ) {
                Text("Whole series")
            }
        },
        dismissButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onThisInstanceOnly,
                    colors = homeBudgetTextButtonColors()
                ) {
                    Text("This instance only")
                }
                TextButton(
                    onClick = onDismiss,
                    colors = homeBudgetTextButtonColors()
                ) {
                    Text("Cancel")
                }
            }
        }
    )
}
