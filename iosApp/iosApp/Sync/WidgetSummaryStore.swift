@preconcurrency import ComposeApp
import Foundation
import WidgetKit

private let spesifyWidgetAppGroupId = "group.it.danielebufarini.spesify"
private let spesifyWidgetKind = "SpesifyWidget"
private let spesifyWidgetMonthTitleKey = "spesify.widget.monthTitle"
private let spesifyWidgetExpenseAmountKey = "spesify.widget.expenseAmount"
private let spesifyWidgetIncomeAmountKey = "spesify.widget.incomeAmount"
private let spesifyWidgetUpdatedAtKey = "spesify.widget.updatedAt"

struct SpesifyWidgetSummarySnapshot: Sendable, Equatable {
    let monthTitle: String
    let expenseAmountText: String
    let incomeAmountText: String
    let updatedAtMillis: Int64
}

@MainActor
final class SpesifyWidgetSummaryRefresher {
    static let shared = SpesifyWidgetSummaryRefresher()

    typealias SummaryLoader = @MainActor () async throws -> SpesifyWidgetSummarySnapshot?
    private let loadSummary: SummaryLoader
    private let defaultsProvider: @MainActor () -> UserDefaults?
    private let reload: @MainActor () -> Void
    private var refreshTask: Task<Void, Never>?
    private var refreshGeneration = 0

    private convenience init() {
        let controller = IosWidgetSummaryController()
        self.init(
            loadSummary: {
                guard let summary = try await controller.loadCurrentMonthSummary().summary else { return nil }
                return SpesifyWidgetSummarySnapshot(
                    monthTitle: summary.monthTitle,
                    expenseAmountText: summary.expenseAmountText,
                    incomeAmountText: summary.incomeAmountText,
                    updatedAtMillis: summary.updatedAtMillis
                )
            },
            defaultsProvider: { UserDefaults(suiteName: spesifyWidgetAppGroupId) },
            reload: { WidgetCenter.shared.reloadTimelines(ofKind: spesifyWidgetKind) }
        )
    }

    init(
        loadSummary: @escaping SummaryLoader,
        defaultsProvider: @escaping @MainActor () -> UserDefaults?,
        reload: @escaping @MainActor () -> Void
    ) {
        self.loadSummary = loadSummary
        self.defaultsProvider = defaultsProvider
        self.reload = reload
    }

    deinit { refreshTask?.cancel() }

    @discardableResult
    func refresh() -> Task<Void, Never> {
        // The singleton owns this work independently of the screen that requested it. A newer
        // refresh replaces an older read, including native results that arrive after cancellation.
        refreshGeneration += 1
        let generation = refreshGeneration
        refreshTask?.cancel()
        let task = Task<Void, Never> { [weak self, loadSummary, defaultsProvider, reload] in
            defer {
                if self?.refreshGeneration == generation { self?.refreshTask = nil }
            }

            do {
                try Task.checkCancellation()
                guard let summary = try await loadSummary() else { return }
                try Task.checkCancellation()
                guard let self, refreshGeneration == generation, let defaults = defaultsProvider() else { return }

                defaults.set(summary.monthTitle, forKey: spesifyWidgetMonthTitleKey)
                defaults.set(summary.expenseAmountText, forKey: spesifyWidgetExpenseAmountKey)
                defaults.set(summary.incomeAmountText, forKey: spesifyWidgetIncomeAmountKey)
                defaults.set(summary.updatedAtMillis, forKey: spesifyWidgetUpdatedAtKey)
                defaults.synchronize()
                reload()
            } catch is CancellationError {
                // Cancellation does not replace the last published summary or request a reload.
            } catch {
                // Refresh is best-effort; retain the stored summary after ordinary load failure.
            }
        }
        refreshTask = task
        return task
    }
}
