import SwiftUI

struct ExpenseGroupingMenuControl: View {
    @Binding var selection: ExpenseGroupingMode

    var body: some View {
        HStack(spacing: 10) {
            Text(appLocalized("Grouped by"))
                .font(.footnote.weight(.semibold))
                .foregroundStyle(.secondary)
                .accessibilityHidden(true)

            Menu {
                groupingButton(title: appLocalized("Category"), mode: .byCategory)
                groupingButton(title: appLocalized("Date"), mode: .byDate)
            } label: {
                HStack(spacing: 6) {
                    Text(selectedTitle)
                        .font(.subheadline.weight(.semibold))
                    Image(systemName: "chevron.down")
                        .font(.caption.weight(.bold))
                }
                .foregroundStyle(AppThemePalette.onSurface)
                .padding(.horizontal, 14)
                .frame(height: 40)
                .appDashboardChromeSurface(cornerRadius: 18)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .frame(minHeight: 44)
            .accessibilityLabel(appLocalized("Grouped by"))
            .accessibilityValue(selectedTitle)

            Spacer(minLength: 0)
        }
        .frame(minHeight: MonthlyTransactionsHeaderLayout.groupingHeight)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var selectedTitle: String {
        switch selection {
        case .byCategory:
            return appLocalized("Category")
        case .byDate:
            return appLocalized("Date")
        }
    }

    @ViewBuilder
    private func groupingButton(title: String, mode: ExpenseGroupingMode) -> some View {
        Button {
            selection = mode
        } label: {
            if selection == mode {
                Label(title, systemImage: "checkmark")
            } else {
                Text(title)
            }
        }
    }
}

enum MonthlyTransactionsHeaderLayout {
    static let selectorTopSpacing: CGFloat = 14
    static let selectorHeight: CGFloat = 54
    static let groupingTopSpacing: CGFloat = 10
    static let groupingHeight: CGFloat = 44
    static let bottomSpacing: CGFloat = 20
    static var reservedTopInset: CGFloat {
        MonthNavigationHeaderLayout.topPadding +
            MonthNavigationHeaderLayout.minHeight +
            selectorTopSpacing +
            selectorHeight +
            groupingTopSpacing +
            groupingHeight +
            bottomSpacing
    }

    static var groupingOnlyReservedTopInset: CGFloat {
        MonthNavigationHeaderLayout.reservedTopInset +
            groupingTopSpacing +
            groupingHeight
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
