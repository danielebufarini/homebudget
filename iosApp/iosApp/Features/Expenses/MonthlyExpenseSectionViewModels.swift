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
    @ObservationIgnored private var observationTask: Task<Void, Never>?

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
        observationTask?.cancel()
    }

    func start() {
        guard observationTask == nil else {
            return
        }

        observationTask = Task { [weak self, observer] in
            for await snapshot in observer.snapshots {
                guard let self else {
                    return
                }
                apply(snapshot: snapshot)
            }
        }
    }

    func stop() {
        observationTask?.cancel()
        observationTask = nil
    }

    func deleteExpense(_ expenseID: String) {
        Task { [observer] in
            try? await observer.deleteExpense(id: expenseID)
        }
    }

    func deleteRecurringExpenseSeries(_ seriesID: String) {
        Task { [observer] in
            try? await observer.deleteRecurringExpenseSeries(seriesId: seriesID)
        }
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
    @ObservationIgnored private var observationTask: Task<Void, Never>?

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
        observationTask?.cancel()
    }

    func start() {
        guard observationTask == nil else {
            return
        }

        observationTask = Task { [weak self, observer] in
            for await snapshot in observer.snapshots {
                guard let self else {
                    return
                }
                apply(snapshot: snapshot)
            }
        }
    }

    func stop() {
        observationTask?.cancel()
        observationTask = nil
    }

    func deleteIncome(_ incomeID: String) {
        Task { [observer] in
            try? await observer.deleteIncome(id: incomeID)
        }
    }

    func deleteRecurringIncomeSeries(_ seriesID: String) {
        Task { [observer] in
            try? await observer.deleteRecurringIncomeSeries(seriesId: seriesID)
        }
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
