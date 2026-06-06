import SwiftUI

struct NativeTransactionAmountCard: View {
    let kind: AddTransactionKind
    @Binding var amount: String
    @State private var showCalculator = false

    var body: some View {
        let calculatedAmount = nativeFormattedPositiveAmountResult(amount)
        let isInvalid = !amount.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && calculatedAmount == nil

        Button {
            showCalculator = true
        } label: {
            AppGlassListCard(verticalPadding: 18) {
                VStack(alignment: .leading, spacing: 12) {
                    Text(appLocalized("Amount"))
                        .font(.headline)
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, alignment: .center)

                    NativeAmountFieldDisplay(
                        prefix: kind.amountPrefix,
                        color: kind.amountColor,
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
                prefix: kind.amountPrefix,
                color: kind.amountColor,
                onApply: { result in
                    amount = result
                    showCalculator = false
                }
            )
            .appGlassSheetPresentation(detents: [.height(560)])
        }
    }
}

enum NativeTransactionEditorChromeLayout {
    static let selectorTopSpacing = MonthlyTransactionsHeaderLayout.selectorTopSpacing
    static var reservedTopInset: CGFloat {
        ExpenseEditorChromeLayout.reservedTopInset +
            selectorTopSpacing +
            MonthlyTransactionsHeaderLayout.selectorHeight
    }
}

extension AddTransactionKind {
    var categoryType: String {
        switch self {
        case .expense:
            return "expense"
        case .income:
            return "income"
        }
    }

    var amountPrefix: String {
        switch self {
        case .expense:
            return "-"
        case .income:
            return "+"
        }
    }

    var amountColor: Color {
        switch self {
        case .expense:
            return Color.red.opacity(0.7)
        case .income:
            return Color.green.opacity(0.75)
        }
    }
}
