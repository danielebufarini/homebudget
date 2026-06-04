@preconcurrency import ComposeApp
import Foundation
import WidgetKit

private let homeBudgetWidgetAppGroupId = "group.it.danielebufarini.homebudget"
private let homeBudgetWidgetKind = "HomeBudgetWidget"
private let homeBudgetWidgetMonthTitleKey = "homebudget.widget.monthTitle"
private let homeBudgetWidgetExpenseAmountKey = "homebudget.widget.expenseAmount"
private let homeBudgetWidgetIncomeAmountKey = "homebudget.widget.incomeAmount"
private let homeBudgetWidgetUpdatedAtKey = "homebudget.widget.updatedAt"

@MainActor
final class HomeBudgetWidgetSummaryRefresher {
    static let shared = HomeBudgetWidgetSummaryRefresher()

    private let controller = IosWidgetSummaryController()

    private init() {}

    func refresh() {
        Task { [controller] in
            guard let result = try? await controller.loadCurrentMonthSummary(),
                  let summary = result.summary,
                  let defaults = UserDefaults(suiteName: homeBudgetWidgetAppGroupId) else {
                    return
            }

            defaults.set(summary.monthTitle, forKey: homeBudgetWidgetMonthTitleKey)
            defaults.set(summary.expenseAmountText, forKey: homeBudgetWidgetExpenseAmountKey)
            defaults.set(summary.incomeAmountText, forKey: homeBudgetWidgetIncomeAmountKey)
            defaults.set(summary.updatedAtMillis, forKey: homeBudgetWidgetUpdatedAtKey)
            defaults.synchronize()

            await MainActor.run {
                WidgetCenter.shared.reloadTimelines(ofKind: homeBudgetWidgetKind)
            }
        }
    }
}
