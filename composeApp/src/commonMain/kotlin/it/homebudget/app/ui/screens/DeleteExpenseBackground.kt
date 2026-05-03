package it.homebudget.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.delete
import homebudget.composeapp.generated.resources.delete_expense
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DeleteSwipeBackground(
    contentDescription: String,
    modifier: Modifier = Modifier,
    shape: Shape? = null
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(if (shape != null) Modifier.clip(shape) else Modifier)
            .background(MaterialTheme.colorScheme.error)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.delete),
                color = MaterialTheme.colorScheme.onError
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onError
            )
        }
    }
}

@Composable
internal fun DeleteExpenseBackground() {
    val isIos = rememberIsIosPlatform()

    DeleteSwipeBackground(
        contentDescription = stringResource(Res.string.delete_expense),
        shape = if (isIos) RoundedCornerShape(20.dp) else MaterialTheme.shapes.medium
    )
}
