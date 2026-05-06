package it.homebudget.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import homebudget.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DeleteConfirmationDialog(
    message: String,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    HomeBudgetDeletionContainer(
        title = stringResource(Res.string.delete),
        message = message,
        onDismiss = onDismiss
    ) {
        HomeBudgetDeletionDialogButton(
            label = stringResource(Res.string.delete),
            isDestructive = true,
            onClick = onDelete
        )
        HomeBudgetDeletionDialogButton(
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
    HomeBudgetDeletionContainer(
        title = title,
        message = message,
        onDismiss = onDismiss
    ) {
        HomeBudgetDeletionDialogButton(
            label = stringResource(Res.string.this_instance_only),
            isDestructive = true,
            onClick = onThisInstanceOnly
        )
        HomeBudgetDeletionDialogButton(
            label = stringResource(Res.string.whole_series),
            isDestructive = true,
            onClick = onWholeSeries
        )
        HomeBudgetDeletionDialogButton(
            label = stringResource(Res.string.cancel),
            onClick = onDismiss
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeBudgetDeletionContainer(
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
        HomeBudgetDeletionCard(
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
private fun HomeBudgetDeletionCard(
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
private fun HomeBudgetDeletionDialogButton(
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
