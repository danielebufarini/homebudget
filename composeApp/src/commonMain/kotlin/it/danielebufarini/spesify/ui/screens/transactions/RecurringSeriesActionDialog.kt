package it.danielebufarini.spesify.ui.screens.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.danielebufarini.spesify.localization.formatResourceArgs
import org.jetbrains.compose.resources.stringResource
import spesify.composeapp.generated.resources.Res
import spesify.composeapp.generated.resources.cancel
import spesify.composeapp.generated.resources.delete
import spesify.composeapp.generated.resources.this_instance_only
import spesify.composeapp.generated.resources.whole_series

@Composable
internal fun TransactionDeleteConfirmationDialog(
    itemDisplayName: String,
    recurringSeriesId: String?,
    deleteTitle: String,
    deleteItemConfirmationMessageTemplate: String,
    recurringDeleteMessageTemplate: String,
    onDeleteItem: () -> Unit,
    onDeleteSeries: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (recurringSeriesId.isNullOrBlank()) {
        DeleteConfirmationDialog(
            message = deleteItemConfirmationMessageTemplate.formatResourceArgs(itemDisplayName),
            onDelete = onDeleteItem,
            onDismiss = onDismiss
        )
    } else {
        RecurringSeriesActionDialog(
            title = deleteTitle,
            message = recurringDeleteMessageTemplate.formatResourceArgs(itemDisplayName),
            onThisInstanceOnly = onDeleteItem,
            onWholeSeries = { onDeleteSeries(recurringSeriesId) },
            onDismiss = onDismiss
        )
    }
}

@Composable
internal fun DeleteConfirmationDialog(
    message: String,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    SpesifyDeletionContainer(
        title = stringResource(Res.string.delete),
        message = message,
        onDismiss = onDismiss
    ) {
        SpesifyDeletionDialogButton(
            label = stringResource(Res.string.delete),
            isDestructive = true,
            onClick = onDelete
        )
        SpesifyDeletionDialogButton(
            label = stringResource(Res.string.cancel),
            onClick = onDismiss
        )
    }
}

@Composable
internal fun RecurringSeriesActionDialog(
    title: String,
    message: String,
    onThisInstanceOnly: () -> Unit,
    onWholeSeries: () -> Unit,
    onDismiss: () -> Unit
) {
    SpesifyDeletionContainer(
        title = title,
        message = message,
        onDismiss = onDismiss
    ) {
        SpesifyDeletionDialogButton(
            label = stringResource(Res.string.this_instance_only),
            isDestructive = true,
            onClick = onThisInstanceOnly
        )
        SpesifyDeletionDialogButton(
            label = stringResource(Res.string.whole_series),
            isDestructive = true,
            onClick = onWholeSeries
        )
        SpesifyDeletionDialogButton(
            label = stringResource(Res.string.cancel),
            onClick = onDismiss
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpesifyDeletionContainer(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    actions: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 6.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        SpesifyDeletionCard(
            title = title,
            message = message,
            actions = actions,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp)
        )
    }
}

@Composable
private fun SpesifyDeletionCard(
    title: String,
    message: String,
    actions: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = actions
            )
        }
    }
}

@Composable
private fun SpesifyDeletionDialogButton(
    label: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = if (isDestructive) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            )
        } else {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        }
    ) {
        Text(label)
    }
}
