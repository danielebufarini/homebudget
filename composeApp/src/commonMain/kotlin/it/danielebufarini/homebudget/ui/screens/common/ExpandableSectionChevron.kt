package it.danielebufarini.homebudget.ui.screens.common

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun ExpandableSectionChevron(
    rotation: Float,
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    contentColor: Color? = null
) {
    val resolvedContainerColor = containerColor ?: MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val resolvedContentColor = contentColor ?: MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = resolvedContainerColor
    ) {
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .padding(2.dp)
                .rotate(rotation),
            tint = resolvedContentColor
        )
    }
}
