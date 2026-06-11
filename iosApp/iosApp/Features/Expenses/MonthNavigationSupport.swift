@preconcurrency import ComposeApp
import SwiftUI

struct MonthCursor: Hashable {
    private static let navigationBridge = IosMonthNavigationBridge()

    let year: Int
    let month: Int

    func previous() -> MonthCursor {
        let cursor = Self.navigationBridge.previous(year: Int32(year), month: Int32(month))
        return MonthCursor(year: Int(cursor.year), month: Int(cursor.month))
    }

    func next() -> MonthCursor {
        let cursor = Self.navigationBridge.next(year: Int32(year), month: Int32(month))
        return MonthCursor(year: Int(cursor.year), month: Int(cursor.month))
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
    static let sideChromeReservedWidth: CGFloat = 42
    static let topPadding: CGFloat = 5
    static let bottomSpacing: CGFloat = 22
    static let minHeight: CGFloat = 76
    static var reservedTopInset: CGFloat { topPadding + minHeight + bottomSpacing }
}

struct DashboardStyleMonthNavigationHeader: View {
    let selectedMonth: MonthCursor
    let titleText: String?
    let amountText: String
    let onPreviousMonth: (() -> Void)?
    let onNextMonth: (() -> Void)?

    init(
        selectedMonth: MonthCursor,
        titleText: String? = nil,
        amountText: String,
        onPreviousMonth: (() -> Void)? = nil,
        onNextMonth: (() -> Void)? = nil
    ) {
        self.selectedMonth = selectedMonth
        self.titleText = titleText
        self.amountText = amountText
        self.onPreviousMonth = onPreviousMonth
        self.onNextMonth = onNextMonth
    }

    var body: some View {
        VStack(spacing: 6) {
            HStack(spacing: 8) {
                if let onPreviousMonth {
                    Button(action: onPreviousMonth) {
                        DashboardMonthChevron(direction: .left)
                            .frame(width: 24, height: 24)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .foregroundStyle(.primary)
                    .accessibilityLabel(appLocalized("Previous month"))
                }

                Text(titleText ?? selectedMonth.label)
                    .font(.system(size: 22, weight: .semibold))
                    .foregroundStyle(.primary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.84)

                if let onNextMonth {
                    Button(action: onNextMonth) {
                        DashboardMonthChevron(direction: .right)
                            .frame(width: 24, height: 24)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .foregroundStyle(.primary)
                    .accessibilityLabel(appLocalized("Next month"))
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .appDashboardMonthHeaderSurface(cornerRadius: 22)

            Text(amountText)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(.secondary)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity)
        .frame(minHeight: MonthNavigationHeaderLayout.minHeight)
        .padding(.horizontal, 16)
    }
}

private struct DashboardMonthChevron: View {
    enum Direction {
        case left
        case right
    }

    let direction: Direction

    var body: some View {
        Canvas { context, size in
            var path = Path()
            switch direction {
            case .left:
                path.move(to: CGPoint(x: size.width * 0.75, y: size.height * 0.15))
                path.addLine(to: CGPoint(x: size.width * 0.30, y: size.height * 0.50))
                path.addLine(to: CGPoint(x: size.width * 0.75, y: size.height * 0.85))
            case .right:
                path.move(to: CGPoint(x: size.width * 0.25, y: size.height * 0.15))
                path.addLine(to: CGPoint(x: size.width * 0.70, y: size.height * 0.50))
                path.addLine(to: CGPoint(x: size.width * 0.25, y: size.height * 0.85))
            }

            context.stroke(
                path,
                with: .color(AppThemePalette.onSurface),
                style: StrokeStyle(lineWidth: 2.5, lineCap: .round, lineJoin: .round)
            )
        }
        .frame(width: 10, height: 10)
        .frame(width: 24, height: 24)
    }
}
