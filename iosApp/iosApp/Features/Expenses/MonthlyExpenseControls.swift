import ComposeApp
import SwiftUI

struct ExpenseGroupingCarousel: View {
    @Binding var selection: ExpenseGroupingMode
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            GlassEffectContainer(spacing: 8) {
                HStack(spacing: 8) {
                    groupingButton(title: appLocalized("Category"), systemImage: "square.grid.2x2", mode: .byCategory)
                    groupingButton(title: appLocalized("Date"), systemImage: "calendar", mode: .byDate)
                }
                .padding(.vertical, 2)
            }
        }
        .scrollBounceBehavior(.basedOnSize, axes: .horizontal)
        .frame(minHeight: MonthlyTransactionsHeaderLayout.groupingHeight)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func groupingButton(title: String, systemImage: String, mode: ExpenseGroupingMode) -> some View {
        let isSelected = selection == mode

        return Group {
            if isSelected {
                Button { select(mode) } label: {
                    groupingLabel(title: title, systemImage: systemImage)
                }
                .buttonStyle(.glassProminent)
            } else {
                Button { select(mode) } label: {
                    groupingLabel(title: title, systemImage: systemImage)
                }
                .buttonStyle(.glass)
            }
        }
        .tint(Color.accentColor)
        .buttonBorderShape(.capsule)
        .accessibilityLabel(appLocalized("View transactions by %@", title))
        .accessibilityIdentifier(mode == .byCategory ? "grouping_category" : "grouping_date")
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }

    private func groupingLabel(title: String, systemImage: String) -> some View {
        Label(title, systemImage: systemImage)
            .font(.caption.weight(.semibold))
            .lineLimit(1)
            .fixedSize(horizontal: true, vertical: false)
            .padding(.horizontal, 4)
            .frame(minHeight: 30)
    }

    private func select(_ mode: ExpenseGroupingMode) {
        guard selection != mode else {
            return
        }

        withAnimation(reduceMotion ? nil : .easeInOut(duration: 0.18)) {
            selection = mode
        }

        playSelectionHaptic()
    }

    private func playSelectionHaptic() {
        UIImpactFeedbackGenerator(style: .light).impactOccurred(intensity: 0.45)
    }
}

enum MonthlyTransactionsHeaderLayout {
    static let selectorTopSpacing: CGFloat = 14
    static let selectorHeight: CGFloat = 54
    static let groupingTopSpacing: CGFloat = 10
    static let groupingHeight: CGFloat = 48
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
