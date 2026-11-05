// Kotlin's CompletableDeferred/StateFlow synchronize every shared fixture field.
// ObjC imports cannot express that Sendable contract; this exception is limited
// to the test that deliberately shares a Kotlin suspend with its cancellation handle.
@preconcurrency import ComposeApp
import Testing

@Suite(.serialized)
@MainActor
struct SkieCancellationTests {
    @Test(arguments: [true, false])
    func suspendCancellationReleasesKotlin(swiftCancels: Bool) async throws {
        let fixture = SkieSuspendFixture()
        let task = Task { try await fixture.read() }
        try await fixture.awaitEntered()
        if swiftCancels { task.cancel() }
        else { fixture.cancelFromKotlin() }
        do {
            _ = try await task.value
            Issue.record("A cancelled suspend returned successfully")
        } catch is CancellationError {
            // SKIE translates Kotlin cancellation for suspend functions.
        } catch {
            Issue.record("Unexpected cancellation error: \(error)")
        }
        try await fixture.awaitCleanup()
        #expect(fixture.didCleanUp)
    }

    @Test(arguments: [true, false])
    func flowCancellationEndsIterationAndReleasesCollector(swiftCancels: Bool) async throws {
        let fixture = SkieReadFixture()
        let task = Task {
            var received = 0
            for await _ in fixture.groupedObserver.states { received += 1 }
            return (received, Task.isCancelled)
        }
        try await fixture.awaitStarted(count: 1)
        if swiftCancels { task.cancel() }
        else { fixture.cancelFromKotlin() }
        let (count, cancelled) = await task.value
        try await fixture.awaitCleanup(count: 1)
        #expect(count == 0)
        #expect(cancelled)
        #expect(fixture.activeCollectors == 0)
        #expect(fixture.cleanedCollectors == 1)
    }

    @Test func normalCompletionIsDistinctFromFailureAndCancellation() async throws {
        let fixture = SkieReadFixture()
        let task = Task {
            var received = 0
            for await _ in fixture.groupedObserver.states { received += 1 }
            return (received, Task.isCancelled)
        }
        try await fixture.awaitStarted(count: 1)
        fixture.completeRead()
        let (count, cancelled) = await task.value
        try await fixture.awaitCleanup(count: 1)
        #expect(count == 0)
        #expect(!cancelled)
        #expect(fixture.activeCollectors == 0)
        #expect(try await SkieSuspendFixture().completedRead() == "completed")
    }
}
