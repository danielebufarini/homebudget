import SwiftUI

struct MonthCursor: Hashable {
    let year: Int
    let month: Int

    func previous() -> MonthCursor {
        month == 1 ? MonthCursor(year: year - 1, month: 12) : MonthCursor(year: year, month: month - 1)
    }

    func next() -> MonthCursor {
        month == 12 ? MonthCursor(year: year + 1, month: 1) : MonthCursor(year: year, month: month + 1)
    }

    var label: String {
        "\(monthName(month)) \(year)"
    }

    var id: String {
        "\(year)-\(month)"
    }
}

private enum MonthSwipeNavigationGesture {
    static let minimumDistance: CGFloat = 32
    static let triggerDistance: CGFloat = 120
    static let axisDominance: CGFloat = 1.35
}

private struct MonthSwipeNavigationModifier: ViewModifier {
    let onPreviousMonth: (() -> Void)?
    let onNextMonth: (() -> Void)?

    func body(content: Content) -> some View {
        content.simultaneousGesture(
            DragGesture(
                minimumDistance: MonthSwipeNavigationGesture.minimumDistance,
                coordinateSpace: .local
            )
            .onEnded { value in
                let horizontalDistance = abs(value.translation.width)
                let verticalDistance = abs(value.translation.height)

                guard
                    horizontalDistance >= MonthSwipeNavigationGesture.triggerDistance,
                    horizontalDistance > verticalDistance * MonthSwipeNavigationGesture.axisDominance
                else {
                    return
                }

                if value.translation.width < 0 {
                    onNextMonth?()
                } else {
                    onPreviousMonth?()
                }
            }
        )
    }
}

extension View {
    func monthSwipeNavigationGesture(
        onPreviousMonth: (() -> Void)?,
        onNextMonth: (() -> Void)?
    ) -> some View {
        modifier(
            MonthSwipeNavigationModifier(
                onPreviousMonth: onPreviousMonth,
                onNextMonth: onNextMonth
            )
        )
    }
}

func monthlyHeaderAmountText(descriptor: String?, amountText: String) -> String {
    guard let descriptor, !descriptor.isEmpty else {
        return amountText
    }

    return "\(descriptor) • \(amountText)"
}

enum MonthNavigationHeaderLayout {
    static let horizontalPadding: CGFloat = 22
    static let topPadding: CGFloat = 16
    static let bottomSpacing: CGFloat = 22
    static let minHeight: CGFloat = 76
    static var reservedTopInset: CGFloat { topPadding + minHeight + bottomSpacing }
}

struct DashboardStyleMonthNavigationHeader: View {
    let selectedMonth: MonthCursor
    let amountText: String
    let onPreviousMonth: () -> Void
    let onNextMonth: () -> Void

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        VStack(spacing: 2) {
            HStack(spacing: 2) {
                Button(action: onPreviousMonth) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 15, weight: .bold))
                        .frame(width: 24, height: 24)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .foregroundStyle(.primary)
                .accessibilityLabel(appLocalized("Previous month"))

                Text(selectedMonth.label)
                    .font(.system(size: 22, weight: .regular))
                    .foregroundStyle(.primary)
                    .lineLimit(1)

                Button(action: onNextMonth) {
                    Image(systemName: "chevron.right")
                        .font(.system(size: 15, weight: .bold))
                        .frame(width: 24, height: 24)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .foregroundStyle(.primary)
                .accessibilityLabel(appLocalized("Next month"))
            }

            Text(amountText)
                .font(.system(size: 15, weight: .semibold))
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
