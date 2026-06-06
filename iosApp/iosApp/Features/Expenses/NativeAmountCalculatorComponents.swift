@preconcurrency import ComposeApp
import SwiftUI

private let nativeAmountExpressionEvaluator = IosAmountExpressionEvaluator()

func nativeFormattedPositiveAmountResult(_ expression: String) -> String? {
    nativeAmountExpressionEvaluator.formattedPositiveResult(expression: expression)
}

struct NativeExpenseAmountCard: View {
    @Binding var amount: String
    let readOnly: Bool
    @State private var showCalculator = false

    var body: some View {
        let calculatedAmount = nativeFormattedPositiveAmountResult(amount)
        let isInvalid = !amount.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && calculatedAmount == nil

        Button {
            if !readOnly {
                showCalculator = true
            }
        } label: {
            AppGlassListCard(verticalPadding: 18) {
                VStack(alignment: .leading, spacing: 12) {
                    Text(appLocalized("Amount"))
                        .font(.headline)
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, alignment: .center)

                    NativeAmountFieldDisplay(
                        prefix: "-",
                        color: Color.red.opacity(0.7),
                        amount: calculatedAmount,
                        isInvalid: isInvalid
                    )
                }
            }
        }
        .buttonStyle(.plain)
        .sheet(isPresented: $showCalculator) {
            NativeAmountCalculatorSheet(
                initialExpression: amount,
                prefix: "-",
                color: Color.red.opacity(0.7),
                onApply: { result in
                    amount = result
                    showCalculator = false
                }
            )
            .appGlassSheetPresentation(detents: [.height(560)])
        }
    }
}

struct NativeAmountFieldDisplay: View {
    let prefix: String
    let color: Color
    let amount: String?
    let isInvalid: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(appLocalized("Amount"))
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(isInvalid ? .red : .secondary)

            HStack(alignment: .firstTextBaseline, spacing: 12) {
                Text("\(prefix) \(appCurrencySymbol())")
                    .font(.system(size: 30, weight: .bold))
                    .foregroundStyle(color)

                Text(amount ?? "—")
                    .font(.system(size: 42, weight: .semibold, design: .rounded))
                    .foregroundStyle(amount == nil ? .secondary : .primary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }

            if isInvalid {
                Text(appLocalized("Enter a complete calculation with a result greater than 0"))
                    .font(.footnote.weight(.medium))
                    .foregroundStyle(.red)
            }
        }
    }
}

struct NativeAmountCalculatorSheet: View {
    @State private var expression: String
    let prefix: String
    let color: Color
    let onApply: (String) -> Void

    init(
        initialExpression: String,
        prefix: String,
        color: Color,
        onApply: @escaping (String) -> Void
    ) {
        _expression = State(initialValue: initialExpression)
        self.prefix = prefix
        self.color = color
        self.onApply = onApply
    }

    var body: some View {
        let calculatedAmount = nativeFormattedPositiveAmountResult(expression)
        let isInvalid = !expression.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && calculatedAmount == nil

        VStack(alignment: .leading, spacing: 16) {
            Text(appLocalized("Calculator"))
                .font(.title2.weight(.semibold))
                .foregroundStyle(.primary)

            AppGlassListCard(verticalPadding: 16) {
                NativeAmountExpressionDisplay(
                    expression: expression,
                    prefix: prefix,
                    color: color,
                    calculatedAmount: calculatedAmount,
                    isInvalid: isInvalid
                )
            }

            NativeAmountCalculatorKeypad(
                expression: $expression,
                applyEnabled: calculatedAmount != nil,
                onApply: {
                    if let calculatedAmount {
                        onApply(calculatedAmount)
                    }
                }
            )
        }
        .padding(.horizontal, 20)
        .padding(.bottom, 22)
    }
}

struct NativeAmountExpressionDisplay: View {
    let expression: String
    let prefix: String
    let color: Color
    let calculatedAmount: String?
    let isInvalid: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(appLocalized("Expression"))
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(isInvalid ? .red : .secondary)

            Text(expression.isEmpty ? "0.00" : expression)
                .font(.system(size: 28, weight: .semibold, design: .rounded))
                .foregroundStyle(expression.isEmpty ? Color.secondary.opacity(0.55) : Color.primary)
                .lineLimit(2)
                .frame(maxWidth: .infinity, alignment: .leading)

            Divider()
                .overlay(Color.white.opacity(0.10))

            Text(appLocalized("Amount"))
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(isInvalid ? .red : .secondary)

            HStack(alignment: .firstTextBaseline, spacing: 12) {
                Text("\(prefix) \(appCurrencySymbol())")
                    .font(.system(size: 30, weight: .bold))
                    .foregroundStyle(color)

                Text(calculatedAmount ?? "—")
                    .font(.system(size: 42, weight: .semibold, design: .rounded))
                    .foregroundStyle(calculatedAmount == nil ? .secondary : .primary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }

            if isInvalid {
                Text(appLocalized("Enter a complete calculation with a result greater than 0"))
                    .font(.footnote.weight(.medium))
                    .foregroundStyle(.red)
            }
        }
    }
}

struct NativeAmountCalculatorKeypad: View {
    @Binding var expression: String
    let applyEnabled: Bool
    let onApply: () -> Void

    var body: some View {
        VStack(spacing: 8) {
            HStack(spacing: 8) {
                calculatorButton("C") { expression = "" }
                calculatorButton("⌫") { expression = nativeDeleteLastExpressionToken(expression) }
                calculatorButton("(") { append("(") }
                calculatorButton(")") { append(")") }
            }
            HStack(spacing: 8) {
                calculatorButton("7") { append("7") }
                calculatorButton("8") { append("8") }
                calculatorButton("9") { append("9") }
                calculatorButton("÷", emphasized: true) { append("÷") }
            }
            HStack(spacing: 8) {
                calculatorButton("4") { append("4") }
                calculatorButton("5") { append("5") }
                calculatorButton("6") { append("6") }
                calculatorButton("×", emphasized: true) { append("×") }
            }
            HStack(spacing: 8) {
                calculatorButton("1") { append("1") }
                calculatorButton("2") { append("2") }
                calculatorButton("3") { append("3") }
                calculatorButton("-", emphasized: true) { append("-") }
            }
            HStack(spacing: 8) {
                calculatorButton("0") { append("0") }
                calculatorButton(".") { append(".") }
                calculatorButton("+", emphasized: true) { append("+") }
                calculatorButton("=", destructive: true, enabled: applyEnabled, action: onApply)
            }
        }
    }

    private func append(_ token: String) {
        expression = nativeAppendCalculatorToken(expression, token: token)
    }

    private func calculatorButton(
        _ text: String,
        weight: CGFloat = 1,
        emphasized: Bool = false,
        destructive: Bool = false,
        enabled: Bool = true,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Text(text)
                .font(.headline.weight(.semibold))
                .frame(maxWidth: .infinity, minHeight: 46)
                .foregroundStyle(emphasized ? .primary : .primary)
                .background(
                    (
                        enabled
                            ? nativeCalculatorButtonBackground(emphasized: emphasized, destructive: destructive)
                            : Color.white.opacity(0.05)
                    ),
                    in: RoundedRectangle(cornerRadius: 16, style: .continuous)
                )
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.5)
        .frame(maxWidth: .infinity)
        .layoutPriority(weight)
    }

    private func nativeCalculatorButtonBackground(emphasized: Bool, destructive: Bool) -> Color {
        if destructive {
            return .red.opacity(0.28)
        }
        return emphasized ? Color.accentColor.opacity(0.24) : Color.white.opacity(0.10)
    }
}

private func nativeAppendCalculatorToken(_ expression: String, token: String) -> String {
    guard expression.count < 80 else {
        return expression
    }

    let operators: Set<String> = ["+", "-", "×", "÷"]
    let nextExpression: String
    if operators.contains(token) {
        let trimmed = expression.trimmingCharacters(in: .whitespacesAndNewlines)
        nextExpression = trimmed.isEmpty ? token : "\(trimmed) \(token) "
    } else {
        nextExpression = expression + token
    }

    return String(nextExpression.prefix(80))
}

private func nativeDeleteLastExpressionToken(_ expression: String) -> String {
    let trimmed = expression.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !trimmed.isEmpty else {
        return ""
    }
    return String(trimmed.dropLast()).trimmingCharacters(in: .whitespacesAndNewlines)
}
