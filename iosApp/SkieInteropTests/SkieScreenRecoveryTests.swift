// Fixture signals use thread-safe Kotlin StateFlow/Channel; ObjC cannot express Sendable.
@preconcurrency import ComposeApp
import Testing
@testable import Spesify

@Suite(.serialized)
@MainActor
struct SkieScreenRecoveryTests {
    @Test func groupedFailureRetainsDataAndCollapsedSectionsAcrossRetry() async throws {
        let fixture = SkieReadFixture()
        let model = GroupedExpensesSectionsViewModel(
            year: 2026, month: 11, kind: .monthly, groupingMode: .byCategory,
            observer: fixture.groupedObserver
        )
        let first = Task { await model.observeSnapshots() }
        try await fixture.awaitStarted(count: 1)
        fixture.sendData()
        fixture.failRead()
        await first.value
        #expect(model.totalAmountText == "12.34")
        #expect(model.sections.map(\.id) == ["Food"])
        #expect(model.expandedSectionIDs == ["Food"])
        #expect(model.readState.showsError)
        #expect(!model.readState.isUnavailable)

        model.expandedSectionIDs = []
        model.retry()
        let retry = try #require(model.readState.task)
        try await fixture.awaitStarted(count: 2)
        fixture.sendData()
        fixture.completeRead()
        await retry.value
        #expect(model.expandedSectionIDs.isEmpty)
        #expect(!model.readState.showsError)
        #expect(fixture.maximumActiveCollectors == 1)
        model.stop()
    }

    @Test func failedInitialReadsStopLoadingAndDoNotShowZeroOrEmptyData() async throws {
        let grouped = SkieReadFixture()
        let expenses = GroupedExpensesSectionsViewModel(
            year: 2026, month: 11, kind: .monthly, groupingMode: .byCategory,
            observer: grouped.groupedObserver
        )
        let expenseTask = Task { await expenses.observeSnapshots() }
        try await grouped.awaitStarted(count: 1)
        grouped.failRead()
        await expenseTask.value
        #expect(expenses.readState.isUnavailable)
        #expect(expenses.totalAmountText == appLocalized("Amount unavailable"))
        #expect(expenses.emptyStateText == appLocalized("Data unavailable"))

        let monthly = SkieReadFixture()
        let incomes = MonthlyIncomesSectionsViewModel(year: 2026, month: 11, observer: monthly.incomesObserver)
        let incomeTask = Task { await incomes.observeSnapshots() }
        try await monthly.awaitStarted(count: 1)
        monthly.failRead()
        await incomeTask.value
        #expect(incomes.readState.isUnavailable)
        #expect(incomes.totalAmountText == appLocalized("Amount unavailable"))
        #expect(incomes.emptyStateText == appLocalized("Data unavailable"))

        let recurring = SkieReadFixture()
        let recurrences = RecurringExpensesSectionsViewModel(year: 2026, month: 11, observer: recurring.recurringObserver)
        let recurringTask = Task { await recurrences.observeSnapshots() }
        try await recurring.awaitStarted(count: 1)
        recurring.failRead()
        await recurringTask.value
        #expect(recurrences.readState.isUnavailable)
        #expect(recurrences.totalAmountText == appLocalized("Amount unavailable"))
        #expect(recurrences.emptyStateTitle == appLocalized("Data unavailable"))
        expenses.stop()
        incomes.stop()
        recurrences.stop()
    }

    @Test func incomeAndRecurringFailuresRetainTheirLastSnapshot() async throws {
        let monthly = SkieReadFixture()
        let incomes = MonthlyIncomesSectionsViewModel(year: 2026, month: 11, observer: monthly.incomesObserver)
        let incomeTask = Task { await incomes.observeSnapshots() }
        try await monthly.awaitStarted(count: 1)
        monthly.sendData()
        monthly.failRead()
        await incomeTask.value
        #expect(incomes.totalAmountText == "12.34")
        #expect(incomes.sections.first?.rows.first?.id == "income")
        #expect(incomes.readState.showsError)
        incomes.expandedSectionIDs = ["date:5 Nov 2026"]
        incomes.retry()
        let retry = try #require(incomes.readState.task)
        try await monthly.awaitStarted(count: 2)
        monthly.sendData()
        monthly.completeRead()
        await retry.value
        #expect(incomes.expandedSectionIDs == ["date:5 Nov 2026"])

        let recurring = SkieReadFixture()
        let recurrences = RecurringExpensesSectionsViewModel(year: 2026, month: 11, observer: recurring.recurringObserver)
        let recurringTask = Task { await recurrences.observeSnapshots() }
        try await recurring.awaitStarted(count: 1)
        recurring.sendData()
        recurring.failRead()
        await recurringTask.value
        #expect(recurrences.totalAmountText == "12.34 / month")
        #expect(recurrences.rows.first?.id == "recurring")
        #expect(recurrences.readState.showsError)
        incomes.stop()
        recurrences.stop()
    }

    @Test(arguments: [false, true])
    func closingDuringReadOrRetryReleasesCollectorAndSuppressesLateState(duringRetry: Bool) async throws {
        let fixture = SkieReadFixture()
        let model = GroupedExpensesSectionsViewModel(
            year: 2026, month: 11, kind: .monthly, groupingMode: .byCategory,
            observer: fixture.groupedObserver
        )
        let first = Task { await model.observeSnapshots() }
        try await fixture.awaitStarted(count: 1)
        if duringRetry {
            fixture.failRead()
            await first.value
            model.retry()
            try await fixture.awaitStarted(count: 2)
        }
        let pending = try #require(model.readState.task)
        model.stop()
        fixture.sendData()
        fixture.failRead()
        await pending.value
        await first.value
        try await fixture.awaitCleanup(count: duringRetry ? 2 : 1)
        #expect(model.sections.isEmpty)
        #expect(!model.readState.showsError)
        #expect(fixture.activeCollectors == 0)
        model.retry()
        #expect(fixture.startedCollectors == (duringRetry ? 2 : 1))
    }
}
