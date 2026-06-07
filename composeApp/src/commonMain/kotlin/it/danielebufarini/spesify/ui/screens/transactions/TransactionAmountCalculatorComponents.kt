package it.danielebufarini.spesify.ui.screens.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.danielebufarini.spesify.data.evaluateAmountExpressionInput
import it.danielebufarini.spesify.data.formatAmountInput
import org.jetbrains.compose.resources.stringResource
import spesify.composeapp.generated.resources.Res
import spesify.composeapp.generated.resources.amount_expression
import spesify.composeapp.generated.resources.calculated_amount
import spesify.composeapp.generated.resources.calculator
import spesify.composeapp.generated.resources.clear
import spesify.composeapp.generated.resources.expense
import spesify.composeapp.generated.resources.income
import spesify.composeapp.generated.resources.invalid_amount_expression

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
