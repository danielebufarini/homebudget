@preconcurrency import ComposeApp
import Testing
@testable import Spesify

@MainActor
struct TransactionSearchExpansionTests {
    @Test
    func collapsedSectionsStayCollapsedWhileNewAndReturningSectionsExpand() {
        let model = TransactionSearchSectionsViewModel(query: "coffee", groupingMode: .byCategory)
        model.apply(snapshot: snapshot(expenses: ["A", "B"], incomes: ["I", "J"]))
        #expect(model.expandedExpenseSectionIDs == ["A", "B"])
        #expect(model.expandedIncomeSectionIDs == ["I", "J"])
        model.expandedExpenseSectionIDs.remove("A")
        model.expandedIncomeSectionIDs.remove("I")

        model.apply(snapshot: snapshot(expenses: ["A", "B", "C"], incomes: ["I", "J", "K"]))
        #expect(model.expandedExpenseSectionIDs == ["B", "C"])
        #expect(model.expandedIncomeSectionIDs == ["J", "K"])
        model.apply(snapshot: snapshot(expenses: ["C"], incomes: ["K"]))
        #expect(model.expandedExpenseSectionIDs == ["C"])
        #expect(model.expandedIncomeSectionIDs == ["K"])
        model.apply(snapshot: snapshot(expenses: ["A", "C"], incomes: ["I", "K"]))
        #expect(model.expandedExpenseSectionIDs == ["A", "C"])
        #expect(model.expandedIncomeSectionIDs == ["I", "K"])
        #expect(model.expenseSections.map(\.id) == ["A", "C"])
        #expect(model.incomeSections.map(\.id) == ["I", "K"])
    }

    @Test
    func emptyFirstSnapshotAndSharedIdsKeepExpenseAndIncomeExpansionIndependent() {
        let model = TransactionSearchSectionsViewModel(query: "coffee", groupingMode: .byDate)
        model.apply(snapshot: snapshot(expenses: [], incomes: []))
        model.apply(snapshot: snapshot(expenses: ["same"], incomes: ["same"]))
        model.expandedExpenseSectionIDs.remove("same")
        model.apply(snapshot: snapshot(expenses: ["same", "new"], incomes: ["same"]))
        #expect(model.expandedExpenseSectionIDs == ["new"])
        #expect(model.expandedIncomeSectionIDs == ["same"])
        #expect(model.hasLoadedSnapshot)
        #expect(model.expenseTotalAmountText == "expense total")
        #expect(model.incomeTotalAmountText == "income total")
    }

    private func snapshot(expenses: [String], incomes: [String]) -> IosTransactionSearchSnapshot {
        IosTransactionSearchSnapshot(
            expenseSnapshot: IosGroupedExpensesSnapshot(
                totalAmountText: "expense total", emptyStateText: "empty expenses",
                sections: expenses.map {
                    IosGroupedExpenseSection(id: $0, title: $0, categoryColorKey: nil, categoryIconKey: nil, totalAmountText: "0", rows: [])
                }
            ),
            incomeSnapshot: IosMonthlyIncomesSnapshot(
                totalAmountText: "income total", emptyStateText: "empty incomes",
                sections: incomes.map {
                    IosIncomeSection(id: $0, title: $0, categoryColorKey: nil, categoryIconKey: nil, totalAmountText: "0", rows: [])
                }
            ),
            canLoadMoreExpenseResults: false, canLoadMoreIncomeResults: false
        )
    }
}
