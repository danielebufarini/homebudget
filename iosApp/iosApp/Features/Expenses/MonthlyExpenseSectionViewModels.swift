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
    let readState = SnapshotReadState()

    private let observer: IosGroupedExpensesObserver
    @ObservationIgnored private var expansionState: GroupedSectionExpansionState

    init(
        year: Int,
        month: Int,
        kind: GroupedExpensesKind,
        groupingMode: ExpenseGroupingMode,
        expandsSectionsInitially: Bool = true,
        observer: IosGroupedExpensesObserver? = nil
    ) {
        self.observer = observer ?? IosGroupedExpensesObserver(
            year: Int32(year),
            month: Int32(month),
            screenType: kind.screenType,
            categoryName: kind.categoryName,
            dayOfMonth: kind.dayOfMonth,
            initialGroupingMode: groupingMode
        )
        expansionState = GroupedSectionExpansionState(
            strategy: NewSectionsExpansionStrategy(expandsInitially: expandsSectionsInitially)
        )
    }

    func observeSnapshots() async {
        startObservation()
        await readState.wait()
    }

    func retry() {
        guard !readState.isStopped else { return }
        startObservation()
    }

    func stop() { readState.stop() }

    private func startObservation() {
        readState.start { [weak self, observer] generation in
            for await state in observer.states {
                guard let self, readState.accepts(generation) else { return }
                switch onEnum(of: state) {
                case let .data(data):
                    apply(snapshot: data.snapshot)
                    readState.receivedData()
                case .failed:
                    readState.receivedFailure()
                    if !readState.hasLoadedData {
                        totalAmountText = appLocalized("Amount unavailable")
                        emptyStateText = appLocalized("Data unavailable")
                    }
                }
            }
        }
    }

    func deleteExpense(_ expenseID: String) {
        ApplicationWriteTasks.shared.launch { [observer] in
            try await observer.deleteExpense(id: expenseID)
        }
    }

    func deleteRecurringExpenseSeries(_ seriesID: String) {
        ApplicationWriteTasks.shared.launch { [observer] in
            try await observer.deleteRecurringExpenseSeries(seriesId: seriesID)
        }
    }

    func updateGroupingMode(_ groupingMode: ExpenseGroupingMode) {
        observer.setGroupingMode(groupingMode: groupingMode)
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
    var expandedSectionIDs = Set<String>()
    let readState = SnapshotReadState()

    private let observer: IosMonthlyIncomesObserver
    @ObservationIgnored private var expansionState = GroupedSectionExpansionState(
        strategy: NewSectionsExpansionStrategy(expandsInitially: false)
    )

    init(
        year: Int,
        month: Int,
        groupingMode: ExpenseGroupingMode = .byDate,
        observer: IosMonthlyIncomesObserver? = nil
    ) {
        self.observer = observer ?? IosMonthlyIncomesObserver(
            year: Int32(year),
            month: Int32(month),
            initialGroupingMode: groupingMode
        )
    }

    func observeSnapshots() async {
        startObservation()
        await readState.wait()
    }

    func retry() {
        guard !readState.isStopped else { return }
        startObservation()
    }

    func stop() { readState.stop() }

    private func startObservation() {
        readState.start { [weak self, observer] generation in
            for await state in observer.states {
                guard let self, readState.accepts(generation) else { return }
                switch onEnum(of: state) {
                case let .data(data):
                    apply(snapshot: data.snapshot)
                    readState.receivedData()
                case .failed:
                    readState.receivedFailure()
                    if !readState.hasLoadedData {
                        totalAmountText = appLocalized("Amount unavailable")
                        emptyStateText = appLocalized("Data unavailable")
                    }
                }
            }
        }
    }

    func deleteIncome(_ incomeID: String) {
        ApplicationWriteTasks.shared.launch { [observer] in
            try await observer.deleteIncome(id: incomeID)
        }
    }

    func deleteRecurringIncomeSeries(_ seriesID: String) {
        ApplicationWriteTasks.shared.launch { [observer] in
            try await observer.deleteRecurringIncomeSeries(seriesId: seriesID)
        }
    }

    func updateGroupingMode(_ groupingMode: ExpenseGroupingMode) {
        observer.setGroupingMode(groupingMode: groupingMode)
    }

    private func apply(snapshot: IosMonthlyIncomesSnapshot) {
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
