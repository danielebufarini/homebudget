@preconcurrency import ComposeApp
import Foundation
import Observation

@MainActor
@Observable
final class TransactionSearchSectionsViewModel {
    var expenseTotalAmountText = appAmountLabel("0.00")
    var incomeTotalAmountText = appAmountLabel("0.00")
    var expenseEmptyStateText = appLocalized("No matching transactions")
    var incomeEmptyStateText = appLocalized("No matching transactions")
    var expenseSections: [GroupedExpenseSectionModel] = []
    var incomeSections: [GroupedExpenseSectionModel] = []
    var hasLoadedSnapshot = false
    var canLoadMoreExpenseResults = false
    var canLoadMoreIncomeResults = false
    var expandedExpenseSectionIDs = Set<String>()
    var expandedIncomeSectionIDs = Set<String>()

    private let observer: IosTransactionSearchObserver
    @ObservationIgnored private var expenseExpansionState = GroupedSectionExpansionState(
        strategy: NewSectionsExpansionStrategy(expandsInitially: true)
    )
    @ObservationIgnored private var incomeExpansionState = GroupedSectionExpansionState(
        strategy: NewSectionsExpansionStrategy(expandsInitially: true)
    )
    @ObservationIgnored private var groupingTask: Task<Void, Never>?
    @ObservationIgnored private var paginationTask: Task<Void, Never>?
    @ObservationIgnored private var paginationGeneration = 0
    @ObservationIgnored private var observing = false

    init(query: String, groupingMode: ExpenseGroupingMode) {
        observer = IosTransactionSearchObserver(
            query: query,
            initialGroupingMode: groupingMode
        )
    }

    func observeSnapshots() async {
        observing = true
        defer {
            observing = false
            cancelPendingTasks()
        }
        do {
            try await observer.start()
            try Task.checkCancellation()
        } catch {
            return
        }

        for await snapshot in observer.snapshots {
            guard !Task.isCancelled else { return }
            guard let snapshot else {
                continue
            }
            apply(snapshot: snapshot)
        }
    }

    func updateGroupingMode(_ groupingMode: ExpenseGroupingMode) {
        guard observing else { return }
        groupingTask?.cancel()
        groupingTask = Task { [observer] in
            guard !Task.isCancelled else { return }
            try? await observer.setGroupingMode(groupingMode: groupingMode)
        }
    }

    func loadMoreResults() {
        guard observing, paginationTask == nil else { return }
        paginationGeneration += 1
        let generation = paginationGeneration
        paginationTask = Task { [weak self, observer] in
            defer {
                if self?.paginationGeneration == generation { self?.paginationTask = nil }
            }
            guard !Task.isCancelled else { return }
            try? await observer.loadMoreResults()
        }
    }

    func deleteExpense(_ expenseID: String) {
        launchMutation { try await $0.deleteExpense(id: expenseID) }
    }

    func deleteIncome(_ incomeID: String) {
        launchMutation { try await $0.deleteIncome(id: incomeID) }
    }

    func deleteRecurringExpenseSeries(_ seriesID: String) {
        launchMutation { try await $0.deleteRecurringExpenseSeries(seriesId: seriesID) }
    }

    func deleteRecurringIncomeSeries(_ seriesID: String) {
        launchMutation { try await $0.deleteRecurringIncomeSeries(seriesId: seriesID) }
    }

    deinit {
        groupingTask?.cancel()
        paginationTask?.cancel()
    }

    private func launchMutation(_ operation: @escaping @MainActor (IosTransactionSearchObserver) async throws -> Void) {
        guard observing else { return }
        ApplicationWriteTasks.shared.launch { [observer] in
            try await operation(observer)
        }
    }

    private func cancelPendingTasks() {
        paginationGeneration += 1
        groupingTask?.cancel()
        paginationTask?.cancel()
        groupingTask = nil
        paginationTask = nil
    }

    func apply(snapshot: IosTransactionSearchSnapshot) {
        expenseTotalAmountText = snapshot.expenseSnapshot.totalAmountText
        incomeTotalAmountText = snapshot.incomeSnapshot.totalAmountText
        expenseEmptyStateText = snapshot.expenseSnapshot.emptyStateText
        incomeEmptyStateText = snapshot.incomeSnapshot.emptyStateText
        canLoadMoreExpenseResults = snapshot.canLoadMoreExpenseResults
        canLoadMoreIncomeResults = snapshot.canLoadMoreIncomeResults
        expenseSections = snapshot.expenseSnapshot.sections.map(GroupedExpenseSectionModel.init)
        incomeSections = snapshot.incomeSnapshot.sections.map {
            GroupedExpenseSectionModel($0)
        }

        hasLoadedSnapshot = true
        updateExpandedSections()
    }

    private func updateExpandedSections() {
        expandedExpenseSectionIDs = expenseExpansionState.nextExpandedSectionIDs(
            current: expandedExpenseSectionIDs, sections: expenseSections
        )
        expandedIncomeSectionIDs = incomeExpansionState.nextExpandedSectionIDs(
            current: expandedIncomeSectionIDs, sections: incomeSections
        )
    }
}
