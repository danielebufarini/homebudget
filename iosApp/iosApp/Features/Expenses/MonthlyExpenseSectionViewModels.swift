@preconcurrency import ComposeApp
import SwiftUI
import Observation

@MainActor
@Observable
final class GroupedExpensesSectionsViewModel {
    var totalAmountText = appAmountLabel("0.00")
    var emptyStateText = appLocalized("No expenses for this month")
    var sections: [GroupedExpenseSectionModel] = []
    var expandedSectionIDs = Set<String>()

    private let observer: IosGroupedExpensesObserver
    @ObservationIgnored private var expansionState: GroupedSectionExpansionState
    @ObservationIgnored private var isObserving = false

    init(
        year: Int,
        month: Int,
        kind: GroupedExpensesKind,
        groupingMode: ExpenseGroupingMode,
        expandsSectionsInitially: Bool = true
    ) {
        observer = IosGroupedExpensesObserver(
            year: Int32(year),
            month: Int32(month),
            screenType: kind.screenType,
            categoryName: kind.categoryName,
            dayOfMonth: kind.dayOfMonth,
            initialGroupingMode: groupingMode.bridgeValue
        )
        expansionState = GroupedSectionExpansionState(
            strategy: NewSectionsExpansionStrategy(expandsInitially: expandsSectionsInitially)
        )
    }

    deinit {
        observer.dispose()
    }

    func start() {
        guard !isObserving else {
            return
        }

        isObserving = true
        observer.start { [weak self] snapshot in
            guard let self else {
                return
            }

            MainActor.assumeIsolated {
                self.apply(snapshot: snapshot)
            }
        }
    }

    func stop() {
        guard isObserving else {
            return
        }

        observer.stop()
        isObserving = false
    }

    func deleteExpense(_ expenseID: String) {
        observer.deleteExpense(id: expenseID)
    }

    func deleteRecurringExpenseSeries(_ seriesID: String) {
        observer.deleteRecurringExpenseSeries(seriesId: seriesID)
    }

    func updateGroupingMode(_ groupingMode: ExpenseGroupingMode) {
        observer.setGroupingMode(groupingMode: groupingMode.bridgeValue)
    }

    private func apply(snapshot: IosGroupedExpensesSnapshot) {
        totalAmountText = snapshot.totalAmountText
        emptyStateText = snapshot.emptyStateText
        sections = snapshot.sections.map { section in
            GroupedExpenseSectionModel(section)
        }
        expandedSectionIDs = expansionState.nextExpandedSectionIDs(
            current: expandedSectionIDs,
            sections: sections
        )
    }
}

@MainActor
@Observable
final class MonthlyIncomesSectionsViewModel {
    var totalAmountText = appAmountLabel("0.00")
    var emptyStateText = appLocalized("No income for this month")
    var sections: [GroupedExpenseSectionModel] = []
    var hasLoadedSnapshot = false
    var expandedSectionIDs = Set<String>()

    private let observer: IosMonthlyIncomesObserver
    @ObservationIgnored private var expansionState = GroupedSectionExpansionState(
        strategy: NewSectionsExpansionStrategy(expandsInitially: false)
    )
    @ObservationIgnored private var isObserving = false

    init(
        year: Int,
        month: Int,
        groupingMode: ExpenseGroupingMode = .byDate
    ) {
        observer = IosMonthlyIncomesObserver(
            year: Int32(year),
            month: Int32(month),
            initialGroupingMode: groupingMode.bridgeValue
        )
    }

    deinit {
        observer.dispose()
    }

    func start() {
        guard !isObserving else {
            return
        }

        isObserving = true
        observer.start { [weak self] snapshot in
            guard let self else {
                return
            }

            MainActor.assumeIsolated {
                self.apply(snapshot: snapshot)
            }
        }
    }

    func stop() {
        guard isObserving else {
            return
        }

        observer.stop()
        isObserving = false
    }

    func deleteIncome(_ incomeID: String) {
        observer.deleteIncome(id: incomeID)
    }

    func deleteRecurringIncomeSeries(_ seriesID: String) {
        observer.deleteRecurringIncomeSeries(seriesId: seriesID)
    }

    func updateGroupingMode(_ groupingMode: ExpenseGroupingMode) {
        observer.setGroupingMode(groupingMode: groupingMode.bridgeValue)
    }

    private func apply(snapshot: IosMonthlyIncomesSnapshot) {
        totalAmountText = snapshot.totalAmountText
        emptyStateText = snapshot.emptyStateText
        sections = snapshot.sections.map { section in
            GroupedExpenseSectionModel(section)
        }
        hasLoadedSnapshot = true
        expandedSectionIDs = expansionState.nextExpandedSectionIDs(
            current: expandedSectionIDs,
            sections: sections
        )
    }
}
