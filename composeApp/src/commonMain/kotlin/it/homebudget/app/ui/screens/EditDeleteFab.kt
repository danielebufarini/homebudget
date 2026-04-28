package it.homebudget.app.ui.screens

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun DeleteEditItemFab(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val containerColor = MaterialTheme.colorScheme.errorContainer
    val contentColor = MaterialTheme.colorScheme.onErrorContainer
    val onFabClick = if (enabled) onClick else ({})

    FloatingActionButton(
        onClick = onFabClick,
        shape = CircleShape,
        containerColor = containerColor,
        contentColor = contentColor,
        modifier = Modifier.navigationBarsPadding()
    ) {
        Icon(
            imageVector = Icons.Filled.Delete,
            contentDescription = label
        )
    }
}
