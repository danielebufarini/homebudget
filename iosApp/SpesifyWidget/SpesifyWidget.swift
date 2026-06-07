import SwiftUI
import WidgetKit

private let appGroupId = "group.it.danielebufarini.spesify"
private let voiceExpenseURL = URL(string: "spesify://voice-expense")!

private enum WidgetKeys {
    static let monthTitle = "spesify.widget.monthTitle"
    static let expenseAmount = "spesify.widget.expenseAmount"
    static let incomeAmount = "spesify.widget.incomeAmount"
}

struct SpesifyWidgetEntry: TimelineEntry {
    let date: Date
    let monthTitle: String
    let expenseAmount: String
    let incomeAmount: String
}

struct SpesifyWidgetProvider: TimelineProvider {
    func placeholder(in context: Context) -> SpesifyWidgetEntry {
        SpesifyWidgetEntry(
            date: Date(),
            monthTitle: currentMonthTitle(),
            expenseAmount: formattedZeroAmount(),
            incomeAmount: formattedZeroAmount()
        )
    }

    func getSnapshot(in context: Context, completion: @escaping (SpesifyWidgetEntry) -> Void) {
        completion(loadEntry())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<SpesifyWidgetEntry>) -> Void) {
        let entry = loadEntry()
        let nextRefresh = Calendar.current.date(byAdding: .minute, value: 30, to: Date()) ?? Date().addingTimeInterval(1800)
        completion(Timeline(entries: [entry], policy: .after(nextRefresh)))
    }

    private func loadEntry() -> SpesifyWidgetEntry {
        let defaults = UserDefaults(suiteName: appGroupId)
        return SpesifyWidgetEntry(
            date: Date(),
            monthTitle: defaults?.string(forKey: WidgetKeys.monthTitle) ?? currentMonthTitle(),
            expenseAmount: defaults?.string(forKey: WidgetKeys.expenseAmount) ?? formattedZeroAmount(),
            incomeAmount: defaults?.string(forKey: WidgetKeys.incomeAmount) ?? formattedZeroAmount()
        )
    }
}

struct SpesifyWidgetEntryView: View {
    @Environment(\.widgetFamily) private var family
    let entry: SpesifyWidgetEntry

    var body: some View {
        VStack(alignment: .leading, spacing: family == .systemMedium ? 10 : 8) {
            Text(entry.monthTitle)
                .font(.headline.weight(.semibold))
                .lineLimit(1)
                .minimumScaleFactor(0.8)

            amountRow(label: widgetLocalized("Expenses"), value: entry.expenseAmount, emphasize: true)
            if family == .systemMedium {
                amountRow(label: widgetLocalized("Income"), value: entry.incomeAmount, emphasize: false)
            }

            Spacer(minLength: 0)

            Link(destination: voiceExpenseURL) {
                Label(widgetLocalized("Add Expense"), systemImage: "mic.fill")
                    .font(.caption.weight(.semibold))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
            }
            .buttonStyle(.borderedProminent)
        }
        .containerBackground(.background, for: .widget)
    }

    private func amountRow(label: String, value: String, emphasize: Bool) -> some View {
        HStack(alignment: .firstTextBaseline) {
            Text(label)
                .font(.caption)
                .foregroundStyle(.secondary)
                .lineLimit(1)
            Spacer(minLength: 8)
            Text(value)
                .font(emphasize ? .title3.weight(.bold) : .headline.weight(.semibold))
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
    }
}

@main
struct SpesifyWidget: Widget {
    let kind = "SpesifyWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: SpesifyWidgetProvider()) { entry in
            SpesifyWidgetEntryView(entry: entry)
        }
        .configurationDisplayName(widgetLocalized("Spesify"))
        .description(widgetLocalized("Current month budget summary"))
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

private func currentMonthTitle() -> String {
    let formatter = DateFormatter()
    formatter.locale = .current
    formatter.setLocalizedDateFormatFromTemplate("MMMM yyyy")
    return formatter.string(from: Date()).capitalized(with: .current)
}

private func formattedZeroAmount() -> String {
    let formatter = NumberFormatter()
    formatter.locale = .current
    formatter.numberStyle = .currency
    formatter.minimumFractionDigits = 2
    formatter.maximumFractionDigits = 2
    return formatter.string(from: 0) ?? "\(Locale.current.currencySymbol ?? "$")0.00"
}

private func widgetLocalized(_ key: String) -> String {
    NSLocalizedString(key, comment: "")
}
