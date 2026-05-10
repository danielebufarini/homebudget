import SwiftUI
import WidgetKit

private let appGroupId = "group.it.homebudget.app"
private let voiceExpenseURL = URL(string: "homebudget://voice-expense")!

private enum WidgetKeys {
    static let monthTitle = "homebudget.widget.monthTitle"
    static let expenseAmount = "homebudget.widget.expenseAmount"
    static let incomeAmount = "homebudget.widget.incomeAmount"
}

struct HomeBudgetWidgetEntry: TimelineEntry {
    let date: Date
    let monthTitle: String
    let expenseAmount: String
    let incomeAmount: String
}

struct HomeBudgetWidgetProvider: TimelineProvider {
    func placeholder(in context: Context) -> HomeBudgetWidgetEntry {
        HomeBudgetWidgetEntry(
            date: Date(),
            monthTitle: currentMonthTitle(),
            expenseAmount: formattedZeroAmount(),
            incomeAmount: formattedZeroAmount()
        )
    }

    func getSnapshot(in context: Context, completion: @escaping (HomeBudgetWidgetEntry) -> Void) {
        completion(loadEntry())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<HomeBudgetWidgetEntry>) -> Void) {
        let entry = loadEntry()
        let nextRefresh = Calendar.current.date(byAdding: .minute, value: 30, to: Date()) ?? Date().addingTimeInterval(1800)
        completion(Timeline(entries: [entry], policy: .after(nextRefresh)))
    }

    private func loadEntry() -> HomeBudgetWidgetEntry {
        let defaults = UserDefaults(suiteName: appGroupId)
        return HomeBudgetWidgetEntry(
            date: Date(),
            monthTitle: defaults?.string(forKey: WidgetKeys.monthTitle) ?? currentMonthTitle(),
            expenseAmount: defaults?.string(forKey: WidgetKeys.expenseAmount) ?? formattedZeroAmount(),
            incomeAmount: defaults?.string(forKey: WidgetKeys.incomeAmount) ?? formattedZeroAmount()
        )
    }
}

struct HomeBudgetWidgetEntryView: View {
    @Environment(\.widgetFamily) private var family
    let entry: HomeBudgetWidgetEntry

    var body: some View {
        VStack(alignment: .leading, spacing: family == .systemMedium ? 10 : 8) {
            Text(entry.monthTitle)
                .font(.headline.weight(.semibold))
                .lineLimit(1)
                .minimumScaleFactor(0.8)

            amountRow(label: localized("Expenses", italian: "Spese"), value: entry.expenseAmount, emphasize: true)

            if family == .systemMedium {
                amountRow(label: localized("Income", italian: "Entrate"), value: entry.incomeAmount, emphasize: false)
            }

            Spacer(minLength: 0)

            Link(destination: voiceExpenseURL) {
                Label(localized("Add Expense", italian: "Aggiungi spesa"), systemImage: "mic.fill")
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
struct HomeBudgetWidget: Widget {
    let kind = "HomeBudgetWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: HomeBudgetWidgetProvider()) { entry in
            HomeBudgetWidgetEntryView(entry: entry)
        }
        .configurationDisplayName("HomeBudget")
        .description(localized("Current month budget summary", italian: "Riepilogo budget del mese corrente"))
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
    let symbol = Locale.current.language.languageCode?.identifier == "it" ? "€" : "$"
    return "\(symbol) 0.00"
}

private func localized(_ english: String, italian: String) -> String {
    Locale.current.language.languageCode?.identifier == "it" ? italian : english
}
