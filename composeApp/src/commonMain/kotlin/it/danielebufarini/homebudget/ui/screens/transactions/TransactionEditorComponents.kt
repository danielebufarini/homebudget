package it.danielebufarini.homebudget.ui.screens.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.amount_expression
import homebudget.composeapp.generated.resources.calculated_amount
import homebudget.composeapp.generated.resources.calculator
import homebudget.composeapp.generated.resources.clear
import homebudget.composeapp.generated.resources.expense
import homebudget.composeapp.generated.resources.income
import homebudget.composeapp.generated.resources.invalid_amount_expression
import it.danielebufarini.homebudget.data.evaluateAmountExpressionInput
import it.danielebufarini.homebudget.data.formatAmountInput
import org.jetbrains.compose.resources.stringResource

enum class TransactionEditorKind {
    Expense,
    Income
}

@Composable
internal fun TransactionKindSelector(
    selectedKind: TransactionEditorKind,
    expenseLabel: String,
    incomeLabel: String,
    onKindSelected: (TransactionEditorKind) -> Unit,
    modifier: Modifier = Modifier
) {
    SoftDepthCard(
        modifier = modifier,
        contentPadding = PaddingValues(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TransactionKindSegment(
                label = expenseLabel,
                icon = ExpenseAmountIcon,
                selected = selectedKind == TransactionEditorKind.Expense,
                onClick = { onKindSelected(TransactionEditorKind.Expense) },
                modifier = Modifier.weight(1f)
            )
            TransactionKindSegment(
                label = incomeLabel,
                icon = IncomeAmountIcon,
                selected = selectedKind == TransactionEditorKind.Income,
                onClick = { onKindSelected(TransactionEditorKind.Income) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TransactionKindSegment(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        },
        label = "transactionKindContainer"
    )
    val contentColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "transactionKindContent"
    )

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransactionAmountHeader(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    kind: TransactionEditorKind,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    isError: Boolean = false,
    supportingText: String? = null,
    currencySymbol: String = "€"
) {
    val expenseLabel = stringResource(Res.string.expense)
    val incomeLabel = stringResource(Res.string.income)
    val expressionLabel = stringResource(Res.string.amount_expression)
    val calculatedAmountLabel = stringResource(Res.string.calculated_amount)
    val invalidExpressionText = stringResource(Res.string.invalid_amount_expression)
    val clearLabel = stringResource(Res.string.clear)
    val calculatorLabel = stringResource(Res.string.calculator)
    var showCalculator by remember { mutableStateOf(false) }
    val accentColor = when (kind) {
        TransactionEditorKind.Expense -> MaterialTheme.colorScheme.error
        TransactionEditorKind.Income -> MaterialTheme.colorScheme.tertiary
    }
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    val evaluatedAmount = remember(value) { evaluateAmountExpressionInput(value) }
    val hasInput = value.isNotBlank()
    val hasValidPositiveAmount = evaluatedAmount != null && evaluatedAmount > 0L
    val showExpressionError = hasInput && !hasValidPositiveAmount
    val effectiveIsError = isError || showExpressionError
    val effectiveSupportingText = supportingText ?: if (showExpressionError) invalidExpressionText else null
    val indicatorColor = androidx.compose.animation.animateColorAsState(
        targetValue = when {
            effectiveIsError -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        },
        label = "amountIndicatorColor"
    ).value
    val sign = if (kind == TransactionEditorKind.Expense) "−" else "+"
    val title = if (kind == TransactionEditorKind.Expense) expenseLabel else incomeLabel
    val amountTextStyle = MaterialTheme.typography.headlineMedium.copy(
        fontWeight = FontWeight.SemiBold,
        color = if (hasValidPositiveAmount) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    )
    val prefixWidth = 68.dp
    val prefixGap = 10.dp
    val fieldShape = RoundedCornerShape(16.dp)
    val calculatedAmountText = evaluatedAmount
        ?.takeIf { it > 0L }
        ?.let(::formatAmountInput)
        ?: "—"

    SoftDepthCard(
        modifier = modifier,
        contentPadding = PaddingValues(20.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clip(fieldShape)
                .background(containerColor)
                .clickable(enabled = !readOnly) { showCalculator = true }
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                text = calculatedAmountLabel,
                style = MaterialTheme.typography.labelLarge,
                color = if (effectiveIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$sign $currencySymbol",
                    modifier = Modifier.width(prefixWidth),
                    style = amountTextStyle,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(prefixGap))
                Text(
                    text = calculatedAmountText,
                    modifier = Modifier.weight(1f),
                    style = amountTextStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .height(if (effectiveIsError) 2.dp else 1.dp)
                    .background(indicatorColor)
            )
            if (effectiveSupportingText != null) {
                Text(
                    text = effectiveSupportingText,
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (effectiveIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (showCalculator) {
            AmountCalculatorBottomSheet(
                initialExpression = value,
                title = calculatorLabel,
                expressionLabel = expressionLabel,
                calculatedAmountLabel = calculatedAmountLabel,
                invalidExpressionText = invalidExpressionText,
                clearLabel = clearLabel,
                sign = sign,
                currencySymbol = currencySymbol,
                accentColor = accentColor,
                onDismiss = { showCalculator = false },
                onApply = { result ->
                    onValueChange(formatAmountInput(result))
                    showCalculator = false
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AmountCalculatorBottomSheet(
    initialExpression: String,
    title: String,
    expressionLabel: String,
    calculatedAmountLabel: String,
    invalidExpressionText: String,
    clearLabel: String,
    sign: String,
    currencySymbol: String,
    accentColor: Color,
    onDismiss: () -> Unit,
    onApply: (Long) -> Unit
) {
    var expression by remember(initialExpression) { mutableStateOf(initialExpression) }
    val evaluatedAmount = remember(expression) { evaluateAmountExpressionInput(expression) }
    val hasInput = expression.isNotBlank()
    val hasValidPositiveAmount = evaluatedAmount != null && evaluatedAmount > 0L
    val showExpressionError = hasInput && !hasValidPositiveAmount
    val calculatedAmountText = evaluatedAmount
        ?.takeIf { it > 0L }
        ?.let(::formatAmountInput)
        ?: "—"
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text(
                    text = expressionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (showExpressionError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = expression.ifBlank { "0.00" },
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (expression.isBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = calculatedAmountLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (showExpressionError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$sign $currencySymbol",
                        modifier = Modifier.width(68.dp),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = calculatedAmountText,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = if (hasValidPositiveAmount) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (showExpressionError) {
                    Text(
                        text = invalidExpressionText,
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            CalculatorKeypad(
                onToken = { token -> expression = appendCalculatorToken(expression, token) },
                onBackspace = { expression = deleteLastExpressionToken(expression) },
                onClear = { expression = "" },
                onApply = { evaluatedAmount?.takeIf { it > 0L }?.let(onApply) },
                clearLabel = clearLabel,
                applyEnabled = hasValidPositiveAmount
            )
        }
    }
}


@Composable
private fun CalculatorKeypad(
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

private fun appendCalculatorToken(expression: String, token: String): String {
    if (expression.length >= MAX_CALCULATOR_EXPRESSION_LENGTH) return expression
    return when (token) {
        "+", "-", "×", "÷" -> {
            val trimmed = expression.trimEnd()
            if (trimmed.isEmpty()) token else "$trimmed $token "
        }
        else -> expression + token
    }.take(MAX_CALCULATOR_EXPRESSION_LENGTH)
}

private fun deleteLastExpressionToken(expression: String): String {
    val trimmed = expression.trimEnd()
    if (trimmed.isEmpty()) return ""
    return trimmed.dropLast(1).trimEnd()
}

private const val MAX_CALCULATOR_EXPRESSION_LENGTH = 80

@Composable
internal fun SoftTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector = Icons.AutoMirrored.Filled.Notes,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val indicatorColor by androidx.compose.animation.animateColorAsState(
        targetValue = when {
            isError -> MaterialTheme.colorScheme.error
            focused -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        },
        label = "softTextFieldIndicatorColor"
    )

    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.clip(RoundedCornerShape(22.dp)),
        readOnly = readOnly,
        enabled = enabled,
        singleLine = singleLine,
        interactionSource = interactionSource,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        supportingText = supportingText?.let { text ->
            { Text(text) }
        },
        isError = isError,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            errorContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedIndicatorColor = indicatorColor,
            unfocusedIndicatorColor = indicatorColor,
            disabledIndicatorColor = indicatorColor,
            errorIndicatorColor = indicatorColor
        )
    )
}
