@preconcurrency import ComposeApp
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
    @ObservationIgnored private var knownExpenseSectionIDs = Set<String>()
    @ObservationIgnored private var knownIncomeSectionIDs = Set<String>()
    @ObservationIgnored private var hasLoadedInitialExpansionState = false

    init(query: String, groupingMode: ExpenseGroupingMode) {
        observer = IosTransactionSearchObserver(
            query: query,
            initialGroupingMode: groupingMode.bridgeValue
        )
    }

    func observeSnapshots() async {
        do {
            try await observer.start()
        } catch {
            return
        }

        for await snapshot in observer.snapshots {
            guard let snapshot else {
                continue
            }
            apply(snapshot: snapshot)
        }
    }

    func updateGroupingMode(_ groupingMode: ExpenseGroupingMode) {
        Task { [observer] in
            try? await observer.setGroupingMode(groupingMode: groupingMode.bridgeValue)
        }
    }

    func loadMoreResults() {
        Task { [observer] in
            try? await observer.loadMoreResults()
        }
    }

    func deleteExpense(_ expenseID: String) {
        Task { [observer] in
            try? await observer.deleteExpense(id: expenseID)
        }
    }

    func deleteIncome(_ incomeID: String) {
        Task { [observer] in
            try? await observer.deleteIncome(id: incomeID)
        }
    }

    func deleteRecurringExpenseSeries(_ seriesID: String) {
        Task { [observer] in
            try? await observer.deleteRecurringExpenseSeries(seriesId: seriesID)
        }
    }

    func deleteRecurringIncomeSeries(_ seriesID: String) {
        Task { [observer] in
            try? await observer.deleteRecurringIncomeSeries(seriesId: seriesID)
        }
    }

    private func apply(snapshot: IosTransactionSearchSnapshot) {
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
        let incomingExpenseIDs = Set(expenseSections.lazy.map(\.id))
        let incomingIncomeIDs = Set(incomeSections.lazy.map(\.id))

        if hasLoadedInitialExpansionState {
            expandedExpenseSectionIDs.formIntersection(incomingExpenseIDs)
            expandedExpenseSectionIDs.formUnion(incomingExpenseIDs.subtracting(knownExpenseSectionIDs))
            expandedIncomeSectionIDs.formIntersection(incomingIncomeIDs)
            expandedIncomeSectionIDs.formUnion(incomingIncomeIDs.subtracting(knownIncomeSectionIDs))
        } else {
            expandedExpenseSectionIDs = incomingExpenseIDs
            expandedIncomeSectionIDs = incomingIncomeIDs
            hasLoadedInitialExpansionState = true
        }

        knownExpenseSectionIDs = incomingExpenseIDs
        knownIncomeSectionIDs = incomingIncomeIDs
    }
}
