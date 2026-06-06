import SwiftUI
import UIKit

struct NativeInstallmentRulerPicker: View {
    let label: String
    let systemImageName: String
    @Binding var value: Int
    let enabled: Bool
    let range: ClosedRange<Int>
    let singlePaymentLabel: String
    let installmentsLabel: String

    @State private var liveValue: CGFloat?
    @State private var dragStartValue: CGFloat?

    private let tickSpacing: CGFloat = 18

    private var displayedContinuousValue: CGFloat {
        liveValue ?? CGFloat(value.clamped(to: range))
    }

    private var displayedValue: Int {
        Int(displayedContinuousValue.rounded()).clamped(to: range)
    }

    private var valueCaption: String {
        displayedValue == 1 ? singlePaymentLabel : installmentsLabel.lowercased()
    }

    var body: some View {
        HStack(spacing: 12) {
            NativeExpenseLeadingIcon(systemImageName: systemImageName)
                .opacity(enabled ? 1 : 0.56)

            VStack(alignment: .leading, spacing: 2) {
                Text(label)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)

                NativeInstallmentRulerCanvas(
                    position: displayedContinuousValue,
                    range: range
                )
                .frame(height: 36)
                .background(.secondary.opacity(0.10), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                .contentShape(Rectangle())
                .gesture(rulerDragGesture)
                .allowsHitTesting(enabled)
            }
            .opacity(enabled ? 1 : 0.56)

            VStack(spacing: 0) {
                Text("\(displayedValue)")
                    .font(.title2.weight(.semibold))
                    .foregroundStyle(.primary)
                    .monospacedDigit()
                    .contentTransition(.numericText(value: Double(displayedValue)))

                Text(valueCaption)
                    .font(.caption2.weight(.medium))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.72)
            }
            .frame(minWidth: 52, maxWidth: 76)
            .opacity(enabled ? 1 : 0.56)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(label)
        .accessibilityValue(valueCaptionForAccessibility(displayedValue))
        .accessibilityAdjustableAction { direction in
            guard enabled else { return }
            switch direction {
            case .increment:
                setValue((value + 1).clamped(to: range), animated: true)
            case .decrement:
                setValue((value - 1).clamped(to: range), animated: true)
            @unknown default:
                break
            }
        }
        .onChange(of: value) { _, newValue in
            guard dragStartValue == nil else { return }
            liveValue = CGFloat(newValue.clamped(to: range))
        }
        .onAppear {
            liveValue = CGFloat(value.clamped(to: range))
        }
    }

    private var rulerDragGesture: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { gesture in
                guard enabled else { return }

                if dragStartValue == nil {
                    dragStartValue = CGFloat(value.clamped(to: range))
                }

                let start = dragStartValue ?? CGFloat(value.clamped(to: range))
                let nextValue = (start - gesture.translation.width / tickSpacing).clamped(
                    min: CGFloat(range.lowerBound),
                    max: CGFloat(range.upperBound)
                )
                let snappedValue = Int(nextValue.rounded()).clamped(to: range)

                liveValue = nextValue
                if snappedValue != value {
                    value = snappedValue
                    playSelectionHaptic()
                }
            }
            .onEnded { gesture in
                guard enabled else { return }

                let start = dragStartValue ?? CGFloat(value.clamped(to: range))
                let projectedValue = (start - gesture.predictedEndTranslation.width / tickSpacing).clamped(
                    min: CGFloat(range.lowerBound),
                    max: CGFloat(range.upperBound)
                )
                let targetValue = Int(projectedValue.rounded()).clamped(to: range)

                dragStartValue = nil
                setValue(targetValue, animated: true)
            }
    }

    private func setValue(_ nextValue: Int, animated: Bool) {
        let clampedValue = nextValue.clamped(to: range)
        if clampedValue != value {
            value = clampedValue
            playSelectionHaptic()
        }

        if animated {
            withAnimation(.interactiveSpring(response: 0.32, dampingFraction: 0.78, blendDuration: 0.08)) {
                liveValue = CGFloat(clampedValue)
            }
        } else {
            liveValue = CGFloat(clampedValue)
        }
    }

    private func valueCaptionForAccessibility(_ count: Int) -> String {
        count == 1 ? singlePaymentLabel : appLocalized("%lld Installments", count)
    }

    private func playSelectionHaptic() {
        UIImpactFeedbackGenerator(style: .light).impactOccurred(intensity: 0.52)
    }
}

private struct NativeInstallmentRulerCanvas: View {
    let position: CGFloat
    let range: ClosedRange<Int>

    private let tickSpacing: CGFloat = 18

    var body: some View {
        Canvas { context, size in
            let centerX = size.width / 2
            let centerY: CGFloat = 14
            let selectedValue = Int(position.rounded()).clamped(to: range)

            var track = Path()
            track.move(to: CGPoint(x: 0, y: centerY))
            track.addLine(to: CGPoint(x: size.width, y: centerY))
            context.stroke(
                track,
                with: .color(.secondary.opacity(0.22)),
                style: StrokeStyle(lineWidth: 1.2, lineCap: .round)
            )

            for tick in range {
                let x = centerX + (CGFloat(tick) - position) * tickSpacing
                guard x >= -tickSpacing && x <= size.width + tickSpacing else {
                    continue
                }

                let distanceFromCenter = abs(CGFloat(tick) - position)
                let isSelected = tick == selectedValue
                let isMajor = tick == range.lowerBound || tick % 5 == 0 || tick == range.upperBound
                let alpha = max(0.22, min(1.0, 1.0 - distanceFromCenter / 8.0))
                let tickHeight: CGFloat = isSelected ? 22 : (isMajor ? 16 : 10)
                let lineWidth: CGFloat = isSelected ? 2.4 : (isMajor ? 1.8 : 1.2)
                let color = isSelected ? Color.accentColor : Color.secondary.opacity(alpha)

                var tickPath = Path()
                tickPath.move(to: CGPoint(x: x, y: centerY - tickHeight / 2))
                tickPath.addLine(to: CGPoint(x: x, y: centerY + tickHeight / 2))
                context.stroke(
                    tickPath,
                    with: .color(color),
                    style: StrokeStyle(lineWidth: lineWidth, lineCap: .round)
                )

                if isSelected || isMajor {
                    let resolvedText = context.resolve(
                        Text("\(tick)")
                            .font(.system(size: 10, weight: .medium, design: .rounded))
                            .foregroundColor(isSelected ? Color.accentColor : Color.secondary.opacity(alpha))
                    )
                    context.draw(
                        resolvedText,
                        at: CGPoint(x: x, y: 28),
                        anchor: .center
                    )
                }
            }

            var indicator = Path()
            indicator.move(to: CGPoint(x: centerX, y: 3))
            indicator.addLine(to: CGPoint(x: centerX, y: size.height - 3))
            context.stroke(
                indicator,
                with: .color(.accentColor),
                style: StrokeStyle(lineWidth: 3, lineCap: .round)
            )
        }
    }
}

private extension Comparable {
    func clamped(min lowerBound: Self, max upperBound: Self) -> Self {
        Swift.min(Swift.max(self, lowerBound), upperBound)
    }
}

private extension Int {
    func clamped(to range: ClosedRange<Int>) -> Int {
        clamped(min: range.lowerBound, max: range.upperBound)
    }
}
