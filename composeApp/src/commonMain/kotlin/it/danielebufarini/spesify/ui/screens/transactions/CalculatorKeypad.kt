package it.danielebufarini.spesify.ui.screens.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun CalculatorKeypad(
    onToken: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onApply: () -> Unit,
    clearLabel: String,
    applyEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CalculatorKeyRow {
            CalculatorKeyButton(text = "C", contentDescription = clearLabel, onClick = onClear)
            CalculatorKeyButton(text = "⌫", contentDescription = "Backspace", onClick = onBackspace)
            CalculatorKeyButton(text = "(", onClick = { onToken("(") })
            CalculatorKeyButton(text = ")", onClick = { onToken(")") })
        }
        CalculatorKeyRow {
            CalculatorKeyButton(text = "7", onClick = { onToken("7") })
            CalculatorKeyButton(text = "8", onClick = { onToken("8") })
            CalculatorKeyButton(text = "9", onClick = { onToken("9") })
            CalculatorKeyButton(text = "÷", emphasized = true, onClick = { onToken("÷") })
        }
        CalculatorKeyRow {
            CalculatorKeyButton(text = "4", onClick = { onToken("4") })
            CalculatorKeyButton(text = "5", onClick = { onToken("5") })
            CalculatorKeyButton(text = "6", onClick = { onToken("6") })
            CalculatorKeyButton(text = "×", emphasized = true, onClick = { onToken("×") })
        }
        CalculatorKeyRow {
            CalculatorKeyButton(text = "1", onClick = { onToken("1") })
            CalculatorKeyButton(text = "2", onClick = { onToken("2") })
            CalculatorKeyButton(text = "3", onClick = { onToken("3") })
            CalculatorKeyButton(text = "-", emphasized = true, onClick = { onToken("-") })
        }
        CalculatorKeyRow {
            CalculatorKeyButton(text = "0", onClick = { onToken("0") })
            CalculatorKeyButton(text = ".", onClick = { onToken(".") })
            CalculatorKeyButton(text = "+", emphasized = true, onClick = { onToken("+") })
            CalculatorKeyButton(
                text = "=",
                emphasized = true,
                destructive = true,
                enabled = applyEnabled,
                onClick = onApply
            )
        }
    }
}

@Composable
private fun CalculatorKeyRow(
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
private fun RowScope.CalculatorKeyButton(
    text: String,
    contentDescription: String = text,
    weight: Float = 1f,
    emphasized: Boolean = false,
    destructive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val containerColor = when {
        destructive -> MaterialTheme.colorScheme.errorContainer
        emphasized -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = when {
        destructive -> MaterialTheme.colorScheme.onErrorContainer
        emphasized -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = Modifier
            .weight(weight)
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (enabled) containerColor else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (enabled) contentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

internal fun appendCalculatorToken(expression: String, token: String): String {
    if (expression.length >= MAX_CALCULATOR_EXPRESSION_LENGTH) return expression
    return when (token) {
        "+", "-", "×", "÷" -> {
            val trimmed = expression.trimEnd()
            if (trimmed.isEmpty()) token else "$trimmed $token "
        }
        else -> expression + token
    }.take(MAX_CALCULATOR_EXPRESSION_LENGTH)
}

internal fun deleteLastExpressionToken(expression: String): String {
    val trimmed = expression.trimEnd()
    if (trimmed.isEmpty()) return ""
    return trimmed.dropLast(1).trimEnd()
}

private const val MAX_CALCULATOR_EXPRESSION_LENGTH = 80
