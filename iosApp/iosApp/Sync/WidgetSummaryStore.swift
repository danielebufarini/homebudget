@preconcurrency import ComposeApp
import Foundation
import WidgetKit

private let spesifyWidgetAppGroupId = "group.it.danielebufarini.spesify"
private let spesifyWidgetKind = "SpesifyWidget"
private let spesifyWidgetMonthTitleKey = "spesify.widget.monthTitle"
private let spesifyWidgetExpenseAmountKey = "spesify.widget.expenseAmount"
private let spesifyWidgetIncomeAmountKey = "spesify.widget.incomeAmount"
private let spesifyWidgetUpdatedAtKey = "spesify.widget.updatedAt"

@MainActor
final class SpesifyWidgetSummaryRefresher {
    static let shared = SpesifyWidgetSummaryRefresher()

    private let controller = IosWidgetSummaryController()

    private init() {}

    func refresh() {
        Task { [controller] in
            guard let result = try? await controller.loadCurrentMonthSummary(),
                  let summary = result.summary,
                  let defaults = UserDefaults(suiteName: spesifyWidgetAppGroupId) else {
                    return
            }

            defaults.set(summary.monthTitle, forKey: spesifyWidgetMonthTitleKey)
            defaults.set(summary.expenseAmountText, forKey: spesifyWidgetExpenseAmountKey)
            defaults.set(summary.incomeAmountText, forKey: spesifyWidgetIncomeAmountKey)
            defaults.set(summary.updatedAtMillis, forKey: spesifyWidgetUpdatedAtKey)
            defaults.synchronize()

            await MainActor.run {
                WidgetCenter.shared.reloadTimelines(ofKind: spesifyWidgetKind)
            }
        }
    }
}
