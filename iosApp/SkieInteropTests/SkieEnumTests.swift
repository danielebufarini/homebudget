import ComposeApp
import Testing

@MainActor
struct SkieEnumTests {
    @Test(arguments: [ExpenseGroupingMode.byCategory, .byDate],
          [IosGroupedExpensesScreenType.monthly, .shared, .category, .day])
    func enumsReachProductionBuildersWithStableResults(mode: ExpenseGroupingMode, screen: IosGroupedExpensesScreenType) {
        let snapshot = SkieGroupingFixture().snapshot(screenType: screen, groupingMode: mode)
        let expectedRows: [String]
        let expectedTotal: String
        switch screen {
        case .monthly, .category: expectedRows = ["a", "b"]; expectedTotal = "EUR 14.34"
        case .shared: expectedRows = ["b"]; expectedTotal = "EUR 2.00"
        case .day: expectedRows = ["a"]; expectedTotal = "EUR 12.34"
        }
        #expect(snapshot.totalAmountText == expectedTotal)
        #expect(snapshot.sections.flatMap(\.rows).map(\.id) == expectedRows)
        switch mode {
        case .byCategory: #expect(snapshot.sections.map(\.id) == ["Food"])
        case .byDate:
            let ids = expectedRows.map { $0 == "a" ? "05 M11 2026" : "04 M11 2026" }
            #expect(snapshot.sections.map(\.id) == ids)
        }
    }
}
