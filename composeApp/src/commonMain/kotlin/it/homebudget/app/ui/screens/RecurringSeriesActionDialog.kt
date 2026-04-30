package it.homebudget.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import it.homebudget.app.localization.LocalStrings

@Composable
internal fun RecurringSeriesActionDialog(
    title: String,
    message: String,
    onThisInstanceOnly: () -> Unit,
    onWholeSeries: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = onWholeSeries,
                colors = homeBudgetTextButtonColors()
            ) {
                Text(strings.wholeSeries)
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
                    Text(strings.thisInstanceOnly)
                }
                TextButton(
                    onClick = onDismiss,
                    colors = homeBudgetTextButtonColors()
                ) {
                    Text(strings.cancel)
                }
            }
        }
    )
}
