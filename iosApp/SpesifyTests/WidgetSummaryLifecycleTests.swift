import Foundation
import Testing
@testable import Spesify

@MainActor
struct WidgetSummaryLifecycleTests {
    @Test
    func onlyTheNewestRefreshPublishesWhenAnOlderLoadFinishesLast() async throws {
        let fixture = try WidgetDefaultsFixture()
        defer { fixture.remove() }
        let loader = GatedWidgetLoader()
        var reloads = 0
        let refresher = SpesifyWidgetSummaryRefresher(
            loadSummary: loader.load,
            defaultsProvider: { fixture.defaults },
            reload: { reloads += 1 }
        )
        let first = refresher.refresh()
        await loader.waitForRequest(1)
        let second = refresher.refresh()
        await loader.waitForRequest(2)
        let newest = snapshot("new", updatedAt: 9_007_199_254_740_993)
        try loader.finishRequest(2, with: .success(newest))
        await second.value
        try loader.finishRequest(1, with: .success(snapshot("old", updatedAt: 1)))
        await first.value

        #expect(fixture.read() == newest)
        #expect(reloads == 1)
        #expect(first.isCancelled)
    }

    @Test
    func cancellationSuppressesAnUncooperativeLateValue() async throws {
        let fixture = try WidgetDefaultsFixture()
        defer { fixture.remove() }
        let loader = GatedWidgetLoader()
        var reloads = 0
        let refresher = SpesifyWidgetSummaryRefresher(
            loadSummary: loader.load,
            defaultsProvider: { fixture.defaults },
            reload: { reloads += 1 }
        )
        let pending = refresher.refresh()
        await loader.waitForRequest(1)
        pending.cancel()
        try loader.finishRequest(1, with: .success(snapshot("late", updatedAt: 1)))
        await pending.value
        #expect(fixture.read() == nil)
        #expect(reloads == 0)
    }

    @Test
    func releasingTheOwnerCancelsPendingRefreshWithoutRetainingIt() async throws {
        let fixture = try WidgetDefaultsFixture()
        defer { fixture.remove() }
        let loader = GatedWidgetLoader()
        var reloads = 0
        var refresher: SpesifyWidgetSummaryRefresher? = SpesifyWidgetSummaryRefresher(
            loadSummary: loader.load,
            defaultsProvider: { fixture.defaults },
            reload: { reloads += 1 }
        )
        weak let releasedOwner = refresher
        let pending = try #require(refresher?.refresh())
        await loader.waitForRequest(1)
        refresher = nil
        #expect(releasedOwner == nil)
        #expect(pending.isCancelled)
        try loader.finishRequest(1, with: .success(snapshot("late", updatedAt: 1)))
        await pending.value
        #expect(fixture.read() == nil)
        #expect(reloads == 0)
    }

    @Test
    func ordinaryFailureKeepsStoredSummaryAndAllowsASubsequentRefresh() async throws {
        let fixture = try WidgetDefaultsFixture()
        defer { fixture.remove() }
        let existing = snapshot("existing", updatedAt: 1)
        fixture.seed(existing)
        let loader = GatedWidgetLoader()
        var reloads = 0
        let refresher = SpesifyWidgetSummaryRefresher(
            loadSummary: loader.load,
            defaultsProvider: { fixture.defaults },
            reload: { reloads += 1 }
        )
        let failed = refresher.refresh()
        await loader.waitForRequest(1)
        try loader.finishRequest(1, with: .failure(WidgetTestError.unavailable))
        await failed.value
        #expect(fixture.read() == existing)
        #expect(reloads == 0)

        let recovery = refresher.refresh()
        await loader.waitForRequest(2)
        let recovered = snapshot("recovered", updatedAt: 2)
        try loader.finishRequest(2, with: .success(recovered))
        await recovery.value
        #expect(fixture.read() == recovered)
        #expect(reloads == 1)
    }

    @Test
    func missingSummaryDoesNotOverwriteStoredValuesOrReload() async throws {
        let fixture = try WidgetDefaultsFixture()
        defer { fixture.remove() }
        let existing = snapshot("existing", updatedAt: 1)
        fixture.seed(existing)
        var reloads = 0
        let refresher = SpesifyWidgetSummaryRefresher(
            loadSummary: { nil }, defaultsProvider: { fixture.defaults }, reload: { reloads += 1 }
        )
        await refresher.refresh().value
        #expect(fixture.read() == existing)
        #expect(reloads == 0)
    }

    @Test
    func unavailableStorageDoesNotRequestAReload() async {
        var reloads = 0
        let summary = snapshot("unwritten", updatedAt: 1)
        let refresher = SpesifyWidgetSummaryRefresher(
            loadSummary: { summary }, defaultsProvider: { nil }, reload: { reloads += 1 }
        )
        await refresher.refresh().value
        #expect(reloads == 0)
    }

    private func snapshot(_ tag: String, updatedAt: Int64) -> SpesifyWidgetSummarySnapshot {
        SpesifyWidgetSummarySnapshot(
            monthTitle: "\(tag) month", expenseAmountText: "\(tag) expense", incomeAmountText: "\(tag) income",
            updatedAtMillis: updatedAt
        )
    }
}

private enum WidgetTestError: Error { case unavailable }

@MainActor
private final class GatedWidgetLoader {
    private var requestCount = 0
    private var entered = Set<Int>()
    private var requests: [Int: CheckedContinuation<SpesifyWidgetSummarySnapshot?, Error>] = [:]
    private var entryWaiters: [Int: [CheckedContinuation<Void, Never>]] = [:]

    // Deliberately ignores cancellation to exercise actual publication after late bridge results.
    func load() async throws -> SpesifyWidgetSummarySnapshot? {
        requestCount += 1
        let id = requestCount
        return try await withCheckedThrowingContinuation { continuation in
            requests[id] = continuation
            entered.insert(id)
            entryWaiters.removeValue(forKey: id)?.forEach { $0.resume() }
        }
    }

    func waitForRequest(_ id: Int) async {
        if !entered.contains(id) {
            await withCheckedContinuation { entryWaiters[id, default: []].append($0) }
        }
    }

    func finishRequest(_ id: Int, with result: Result<SpesifyWidgetSummarySnapshot?, Error>) throws {
        let pending = requests.removeValue(forKey: id)
        let continuation = try #require(pending)
        continuation.resume(with: result)
    }
}

private struct WidgetDefaultsFixture {
    let suiteName: String
    let defaults: UserDefaults

    init() throws {
        suiteName = "it.danielebufarini.spesify.tests.widget.\(UUID().uuidString)"
        defaults = try #require(UserDefaults(suiteName: suiteName))
        defaults.removePersistentDomain(forName: suiteName)
    }

    func read() -> SpesifyWidgetSummarySnapshot? {
        guard let month = defaults.string(forKey: "spesify.widget.monthTitle"),
              let expense = defaults.string(forKey: "spesify.widget.expenseAmount"),
              let income = defaults.string(forKey: "spesify.widget.incomeAmount"),
              let updatedAt = defaults.object(forKey: "spesify.widget.updatedAt") as? NSNumber else { return nil }
        return SpesifyWidgetSummarySnapshot(
            monthTitle: month, expenseAmountText: expense, incomeAmountText: income, updatedAtMillis: updatedAt.int64Value
        )
    }

    func seed(_ snapshot: SpesifyWidgetSummarySnapshot) {
        defaults.set(snapshot.monthTitle, forKey: "spesify.widget.monthTitle")
        defaults.set(snapshot.expenseAmountText, forKey: "spesify.widget.expenseAmount")
        defaults.set(snapshot.incomeAmountText, forKey: "spesify.widget.incomeAmount")
        defaults.set(snapshot.updatedAtMillis, forKey: "spesify.widget.updatedAt")
    }

    func remove() { defaults.removePersistentDomain(forName: suiteName) }
}
