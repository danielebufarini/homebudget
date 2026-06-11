import SwiftUI

enum TransactionSearchHeaderLayout {
    static var reservedTopInset: CGFloat {
        MonthNavigationHeaderLayout.topPadding +
            MonthNavigationHeaderLayout.minHeight +
            MonthlyTransactionsHeaderLayout.selectorTopSpacing +
            MonthlyTransactionsHeaderLayout.selectorHeight +
            MonthlyTransactionsHeaderLayout.groupingTopSpacing +
            MonthlyTransactionsHeaderLayout.groupingHeight +
            MonthlyTransactionsHeaderLayout.bottomSpacing
    }
}

struct TransactionSearchGlassHeader: View {
    let query: String
    let amountText: String

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        VStack(spacing: 4) {
            Text(appLocalized("Search Results"))
                .font(.system(size: 22, weight: .regular))
                .foregroundStyle(.primary)
                .lineLimit(1)

            Text(query)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(.secondary)
                .lineLimit(1)

            Text(amountText)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(.secondary)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity)
        .frame(minHeight: MonthNavigationHeaderLayout.minHeight)
        .padding(.horizontal, 16)
        .appGlassSurface(cornerRadius: 20)
        .shadow(
            color: Color.black.opacity(colorScheme == .dark ? 0.26 : 0.10),
            radius: 18,
            x: 0,
            y: 10
        )
    }
}
