import ComposeApp
import Testing

private enum ReadKind: CaseIterable, Sendable { case grouped, incomes, recurring, categories }

@Suite(.serialized)
@MainActor
struct SkieReadStateTests {
    @Test(arguments: ReadKind.allCases, [false, true])
    fileprivate func errorsAndManualRecollectionCrossTheRealBridge(kind: ReadKind, afterData: Bool) async throws {
        let fixture = SkieReadFixture()
        let first = Task { await events(fixture, kind: kind) }
        try await fixture.awaitStarted(count: 1)
        if afterData { fixture.sendData() }
        fixture.failRead()
        #expect(await first.value == (afterData ? ["Data", "Failed"] : ["Failed"]))
        try await fixture.awaitCleanup(count: 1)
        #expect(fixture.activeCollectors == 0)
        #expect(fixture.startedCollectors == 1)

        let retry = Task { await events(fixture, kind: kind) }
        try await fixture.awaitStarted(count: 2)
        fixture.sendData()
        fixture.completeRead()
        #expect(await retry.value == ["Data"])
        try await fixture.awaitCleanup(count: 2)
        #expect(fixture.activeCollectors == 0)
        #expect(fixture.maximumActiveCollectors == 1)
    }

    private func events(_ fixture: SkieReadFixture, kind: ReadKind) async -> [String] {
        var result: [String] = []
        switch kind {
        case .grouped:
            for await state in fixture.groupedObserver.states {
                switch onEnum(of: state) {
                case let .data(data):
                    #expect(data.snapshot.totalAmountText == "12.34")
                    result.append("Data")
                case .failed: result.append("Failed")
                }
            }
        case .incomes:
            for await state in fixture.incomesObserver.states {
                switch onEnum(of: state) {
                case let .data(data):
                    #expect(data.snapshot.totalAmountText == "12.34")
                    result.append("Data")
                case .failed: result.append("Failed")
                }
            }
        case .recurring:
            for await state in fixture.recurringObserver.states {
                switch onEnum(of: state) {
                case let .data(data):
                    #expect(data.snapshot.totalAmountText == "12.34 / month")
                    result.append("Data")
                case .failed: result.append("Failed")
                }
            }
        case .categories:
            for await state in fixture.categoriesController.states() {
                switch onEnum(of: state) {
                case let .data(data):
                    #expect(data.snapshot.categories.map(\.id) == ["expense"])
                    result.append("Data")
                case .failed: result.append("Failed")
                }
            }
        }
        return result
    }
}
