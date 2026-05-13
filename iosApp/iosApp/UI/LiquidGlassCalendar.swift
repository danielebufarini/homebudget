@preconcurrency import ComposeApp
import SwiftUI

private let iosNativeDatePickerRequestNotification = "HomeBudget.IosNativeDatePicker.Request"

private struct NativeDatePickerRequest: Identifiable {
    let id: String
    let initialDate: Date
}

private struct IosNativeDatePickerHostModifier: ViewModifier {
    @State private var request: NativeDatePickerRequest?

    func body(content: Content) -> some View {
        content
            .sheet(item: $request) { request in
                LiquidGlassDatePickerSheet(
                    initialDate: request.initialDate,
                    onCancel: {
                        IosNativeDatePickerBridgeKt.cancelIosNativeDatePickerRequest(requestId: request.id)
                        self.request = nil
                    },
                    onConfirm: { selectedDate in
                        IosNativeDatePickerBridgeKt.completeIosNativeDatePickerRequest(
                            requestId: request.id,
                            selectedDateMillis: Int64(selectedDate.timeIntervalSince1970 * 1000.0)
                        )
                        self.request = nil
                    }
                )
                .appGlassSheetPresentation(detents: [.height(610)])
            }
            .onReceive(
                NotificationCenter.default.publisher(
                    for: Notification.Name(iosNativeDatePickerRequestNotification)
                )
            ) { notification in
                guard let userInfo = notification.userInfo,
                      let requestId = userInfo["requestId"] as? String else {
                    return
                }

                let initialMillis = Int64(userInfo["initialDateMillis"] as? String ?? "")
                let initialDate = initialMillis.map { Date(timeIntervalSince1970: Double($0) / 1000.0) } ?? Date()
                request = NativeDatePickerRequest(
                    id: requestId,
                    initialDate: initialDate
                )
            }
    }
}

extension View {
    func iosNativeDatePickerHost() -> some View {
        modifier(IosNativeDatePickerHostModifier())
    }
}

struct LiquidGlassDatePickerSheet: View {
    let initialDate: Date
    let onCancel: () -> Void
    let onConfirm: (Date) -> Void

    @State private var selectedDate: Date
    @State private var displayedMonth: Date

    init(
        initialDate: Date,
        onCancel: @escaping () -> Void,
        onConfirm: @escaping (Date) -> Void
    ) {
        self.initialDate = initialDate
        self.onCancel = onCancel
        self.onConfirm = onConfirm

        let startOfMonth = Calendar.current.date(
            from: Calendar.current.dateComponents([.year, .month], from: initialDate)
        ) ?? initialDate

        _selectedDate = State(initialValue: initialDate)
        _displayedMonth = State(initialValue: startOfMonth)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(appLocalized("Select a date"))
                .font(.largeTitle.weight(.bold))
                .foregroundStyle(.primary)
                .padding(.horizontal, 20)
                .padding(.top, 10)

            ScrollView {
                VStack(spacing: 16) {
                    LiquidGlassCalendar(
                        selectedDate: $selectedDate,
                        displayedMonth: $displayedMonth
                    )
                }
                .padding(.horizontal, 20)
            }

            HStack(spacing: 12) {
                Button(appLocalized("Cancel"), action: onCancel)
                    .buttonStyle(.glass)
                    .frame(maxWidth: .infinity)

                Button(appLocalized("Save")) {
                    onConfirm(selectedDate)
                }
                .buttonStyle(.glassProminent)
                .frame(maxWidth: .infinity)
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 16)
        }
        .appGlassSheetChrome()
    }
}

struct LiquidGlassCalendar: View {
    @Binding var selectedDate: Date
    @Binding var displayedMonth: Date

    private let calendar = Calendar.current

    private var monthTitle: String {
        displayedMonth.formatted(.dateTime.month(.wide).year())
    }

    private var weekdaySymbols: [String] {
        let formatter = DateFormatter()
        formatter.locale = Locale.current
        return formatter.shortStandaloneWeekdaySymbols
    }

    private var days: [Date?] {
        guard let range = calendar.range(of: .day, in: .month, for: displayedMonth),
              let firstOfMonth = calendar.date(
                from: calendar.dateComponents([.year, .month], from: displayedMonth)
              ) else {
            return []
        }

        let firstWeekday = calendar.component(.weekday, from: firstOfMonth)
        let leadingCount = (firstWeekday - calendar.firstWeekday + 7) % 7

        var result = Array<Date?>(repeating: nil, count: leadingCount)
        for day in range {
            result.append(calendar.date(byAdding: .day, value: day - 1, to: firstOfMonth))
        }

        while result.count % 7 != 0 {
            result.append(nil)
        }

        return result
    }

    var body: some View {
        GlassEffectContainer(spacing: 14) {
            VStack(spacing: 18) {
                HStack {
                    Button(action: previousMonth) {
                        Image(systemName: "chevron.left")
                            .font(.body.weight(.semibold))
                            .frame(width: 36, height: 36)
                    }
                    .buttonStyle(.glass)

                    Spacer()

                    Text(monthTitle)
                        .font(.title3.weight(.semibold))
                        .foregroundStyle(.secondary)

                    Spacer()

                    Button(action: nextMonth) {
                        Image(systemName: "chevron.right")
                            .font(.body.weight(.semibold))
                            .frame(width: 36, height: 36)
                    }
                    .buttonStyle(.glass)
                }

                LazyVGrid(
                    columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: 7),
                    spacing: 12
                ) {
                    ForEach(Array(weekdaySymbols.enumerated()), id: \.offset) { index, symbol in
                        Text(symbol)
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.secondary)
                            .frame(maxWidth: .infinity)
                    }

                    ForEach(Array(days.enumerated()), id: \.offset) { index, date in
                        if let date {
                            LiquidGlassCalendarCell(
                                date: date,
                                selected: calendar.isDate(date, inSameDayAs: selectedDate),
                                today: calendar.isDateInToday(date)
                            ) {
                                selectedDate = date
                            }
                        } else {
                            Color.clear
                                .frame(height: 44)
                        }
                    }
                }
            }
            .padding(18)
            .glassEffect(
                .regular,
                in: RoundedRectangle(cornerRadius: 28, style: .continuous)
            )
        }
        .frame(maxWidth: .infinity)
    }

    private func previousMonth() {
        if let previous = calendar.date(byAdding: .month, value: -1, to: displayedMonth) {
            displayedMonth = previous
        }
    }

    private func nextMonth() {
        if let next = calendar.date(byAdding: .month, value: 1, to: displayedMonth) {
            displayedMonth = next
        }
    }
}

private struct LiquidGlassCalendarCell: View {
    let date: Date
    let selected: Bool
    let today: Bool
    let action: () -> Void

    private let calendar = Calendar.current

    var body: some View {
        Button(action: action) {
            Text("\(calendar.component(.day, from: date))")
                .font(.headline.weight(selected || today ? .semibold : .regular))
                .foregroundStyle(selected ? Color.accentColor : .primary)
                .frame(width: 44, height: 44)
                .background(
                    Circle()
                        .fill(selected ? Color.accentColor.opacity(0.18) : Color.clear)
                )
                .overlay(
                    Circle()
                        .stroke(
                            today && !selected ? Color.accentColor.opacity(0.5) : Color.clear,
                            lineWidth: 1
                        )
                )
        }
        .buttonStyle(.plain)
        .glassEffect(
            .regular.interactive(true),
            in: Circle()
        )
    }
}
