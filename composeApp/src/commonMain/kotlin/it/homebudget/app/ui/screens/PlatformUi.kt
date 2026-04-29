package it.homebudget.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import it.homebudget.app.getPlatform

@Composable
internal fun rememberIsIosPlatform(): Boolean = remember { getPlatform().isIos }

@Composable
internal fun PlatformCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val isIos = rememberIsIosPlatform()
    val cardModifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier

    Card(
        modifier = cardModifier,
        shape = if (isIos) {
            RoundedCornerShape(20.dp)
        } else {
            MaterialTheme.shapes.medium
        },
        colors = CardDefaults.cardColors(
            containerColor = if (isIos) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        border = if (isIos) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        } else {
            null
        },
        elevation = CardDefaults.cardElevation(defaultElevation = if (isIos) 0.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content
        )
    }
}

@Composable
internal fun PlatformTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    val isIos = rememberIsIosPlatform()

    if (isIos) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            readOnly = readOnly,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = trailingIcon,
            singleLine = true,
            keyboardOptions = keyboardOptions,
            shape = RoundedCornerShape(14.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                disabledContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            )
        )
    } else {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            readOnly = readOnly,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = trailingIcon,
            singleLine = true,
            keyboardOptions = keyboardOptions
        )
    }
}
