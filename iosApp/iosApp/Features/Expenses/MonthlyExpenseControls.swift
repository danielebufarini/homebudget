import SwiftUI

struct ExpenseGroupingCarousel: View {
    @Binding var selection: ExpenseGroupingMode
    @Namespace private var selectionNamespace

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 2) {
                groupingButton(title: appLocalized("Category"), mode: .byCategory)
                groupingButton(title: appLocalized("Date"), mode: .byDate)
            }
            .padding(.horizontal, 4)
            .padding(.vertical, 3)
        }
        .scrollBounceBehavior(.basedOnSize, axes: .horizontal)
        .background(
            AppThemePalette.chromeSurface.opacity(0.34),
            in: RoundedRectangle(cornerRadius: 22, style: .continuous)
        )
        .overlay {
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .stroke(AppThemePalette.chromeStroke.opacity(0.58), lineWidth: 1)
        }
        .frame(minHeight: MonthlyTransactionsHeaderLayout.groupingHeight)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func groupingButton(title: String, mode: ExpenseGroupingMode) -> some View {
        let isSelected = selection == mode

        return Button {
            select(mode)
        } label: {
            Text(title)
                .font(.subheadline.weight(isSelected ? .semibold : .medium))
                .foregroundStyle(isSelected ? AppThemePalette.onSurface : AppThemePalette.onSurface.opacity(0.66))
                .lineLimit(1)
                .minimumScaleFactor(0.82)
                .padding(.horizontal, 14)
                .frame(minWidth: 78, minHeight: 40)
                .background {
                    if isSelected {
                        RoundedRectangle(cornerRadius: 19, style: .continuous)
                            .fill(AppThemePalette.chromeSurface.opacity(0.84))
                            .matchedGeometryEffect(id: "selected-expense-grouping-mode", in: selectionNamespace)
                            .overlay {
                                RoundedRectangle(cornerRadius: 19, style: .continuous)
                                    .stroke(AppThemePalette.chromeStroke, lineWidth: 1)
                            }
                    }
                }
                .contentShape(Rectangle())
                .animation(.easeInOut(duration: 0.18), value: isSelected)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(appLocalized("View transactions by %@", title))
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }

    private func select(_ mode: ExpenseGroupingMode) {
        guard selection != mode else {
            return
        }

        withAnimation(.easeInOut(duration: 0.18)) {
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
