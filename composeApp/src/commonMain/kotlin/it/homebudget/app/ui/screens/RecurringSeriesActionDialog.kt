package it.homebudget.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.cancel
import homebudget.composeapp.generated.resources.this_instance_only
import homebudget.composeapp.generated.resources.whole_series
import org.jetbrains.compose.resources.stringResource

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
                Text(stringResource(Res.string.whole_series))
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
                    Text(stringResource(Res.string.this_instance_only))
                }
                TextButton(
                    onClick = onDismiss,
                    colors = homeBudgetTextButtonColors()
                ) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        }
    )
}
