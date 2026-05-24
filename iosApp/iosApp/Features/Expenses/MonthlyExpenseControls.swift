import SwiftUI

struct ExpenseGroupingGlassControl: View {
    @Binding var selection: ExpenseGroupingMode

    var body: some View {
        GlassEffectContainer(spacing: 12) {
            HStack(spacing: 12) {
                ExpenseGroupingGlassButton(
                    title: appLocalized("By Category"),
                    isSelected: selection == .byCategory
                ) {
                    selection = .byCategory
                }

                ExpenseGroupingGlassButton(
                    title: appLocalized("By Date"),
                    isSelected: selection == .byDate
                ) {
                    selection = .byDate
                }
            }
        }
    }
}

private struct ExpenseGroupingGlassButton: View {
    let title: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Group {
            if isSelected {
                Button(title, action: action)
                    .buttonStyle(.glassProminent)
            } else {
                Button(title, action: action)
                    .buttonStyle(.glass)
            }
        }
        .font(.subheadline.weight(.semibold))
    }
}

enum MonthlyTransactionsHeaderLayout {
    static let selectorTopSpacing: CGFloat = 14
    static let selectorHeight: CGFloat = 54
    static let bottomSpacing: CGFloat = 20
    static var reservedTopInset: CGFloat {
        MonthNavigationHeaderLayout.topPadding +
            MonthNavigationHeaderLayout.minHeight +
            selectorTopSpacing +
            selectorHeight +
            bottomSpacing
    }
}

struct MonthlyTransactionKindGlassControl: View {
    @Binding var selection: AddTransactionKind

    var body: some View {
        GlassEffectContainer(spacing: 12) {
            HStack(spacing: 12) {
                MonthlyTransactionKindGlassButton(
                    title: appLocalized("Expenses"),
                    systemImage: "cart.fill",
                    isSelected: selection == .expense
                ) {
                    selection = .expense
                }

                MonthlyTransactionKindGlassButton(
                    title: appLocalized("Income"),
                    systemImage: "banknote.fill",
                    isSelected: selection == .income
                ) {
                    selection = .income
                }
            }
        }
        .frame(height: MonthlyTransactionsHeaderLayout.selectorHeight)
    }
}

private struct MonthlyTransactionKindGlassButton: View {
    let title: String
    let systemImage: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Group {
            if isSelected {
                Button(action: action) {
                    label
                }
                .buttonStyle(.glassProminent)
            } else {
                Button(action: action) {
                    label
                }
                .buttonStyle(.glass)
            }
        }
        .font(.subheadline.weight(.semibold))
    }

    private var label: some View {
        HStack(spacing: 8) {
            Image(systemName: systemImage)
            Text(title)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity)
        .frame(height: 40)
        .contentShape(Rectangle())
    }
}
