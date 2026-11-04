import Testing
@testable import Spesify

@MainActor
struct ApplicationWriteTasksTests {
    @Test
    func acceptedWriteOutlivesItsScreenAndReleasesTheOwnedHandle() async throws {
        let writes = ApplicationWriteTasks()
        let gate = ApplicationWriteGate()
        let store = ApplicationWriteCompletions()
        var screen: ApplicationWriteScreen? = ApplicationWriteScreen(writes: writes)
        weak let releasedScreen = screen
        let accepted = screen?.accept {
            await gate.wait()
            store.values.append("committed")
        }
        let task = try #require(accepted)
        await gate.waitUntilEntered()
        #expect(writes.pendingCount == 1)
        screen = nil
        #expect(releasedScreen == nil)
        #expect(!task.isCancelled)
        gate.open()
        try await task.value
        #expect(store.values == ["committed"])
        #expect(writes.pendingCount == 0)
    }

    @Test
    func independentAcceptedWritesKeepTheirOwnHandlesUntilEachCompletes() async throws {
        let writes = ApplicationWriteTasks()
        let firstGate = ApplicationWriteGate()
        let secondGate = ApplicationWriteGate()
        let store = ApplicationWriteCompletions()
        let first = writes.launch {
            await firstGate.wait()
            store.values.append("first")
        }
        let second = writes.launch {
            await secondGate.wait()
            store.values.append("second")
        }
        await firstGate.waitUntilEntered()
        await secondGate.waitUntilEntered()
        #expect(writes.pendingCount == 2)
        #expect(!first.isCancelled)
        secondGate.open()
        try await second.value
        #expect(writes.pendingCount == 1)
        #expect(store.values == ["second"])
        firstGate.open()
        try await first.value
        #expect(store.values == ["second", "first"])
        #expect(writes.pendingCount == 0)
    }

    @Test
    func explicitCancellationDoesNotReportSuccessAndAlwaysReleasesTheHandle() async {
        let writes = ApplicationWriteTasks()
        let started = AsyncStream<Void>.makeStream()
        let store = ApplicationWriteCompletions()
        let task = writes.launch {
            started.continuation.yield(())
            try await Task.sleep(for: .seconds(60))
            store.values.append("committed")
        }
        var events = started.stream.makeAsyncIterator()
        await events.next()
        #expect(writes.pendingCount == 1)
        task.cancel()
        do {
            try await task.value
            Issue.record("Cancelled write reported successful completion")
        } catch {
            #expect(error is CancellationError)
        }
        #expect(store.values.isEmpty)
        #expect(writes.pendingCount == 0)
    }

    @Test
    func ordinaryFailureIsPreservedInsteadOfBecomingSuccess() async {
        let writes = ApplicationWriteTasks()
        let task = writes.launch { throw ApplicationWriteFailure.persistence }
        do {
            try await task.value
            Issue.record("Failed write reported successful completion")
        } catch {
            #expect(error as? ApplicationWriteFailure == .persistence)
        }
        #expect(writes.pendingCount == 0)
    }

    @Test
    func releasingTheApplicationOwnerCancelsOutstandingWork() async throws {
        var writes: ApplicationWriteTasks? = ApplicationWriteTasks()
        weak let releasedOwner = writes
        let started = AsyncStream<Void>.makeStream()
        let store = ApplicationWriteCompletions()
        let accepted = writes?.launch {
            started.continuation.yield(())
            try await Task.sleep(for: .seconds(60))
            store.values.append("committed")
        }
        let task = try #require(accepted)
        var events = started.stream.makeAsyncIterator()
        await events.next()
        writes = nil
        #expect(releasedOwner == nil)
        #expect(task.isCancelled)
        do {
            try await task.value
            Issue.record("Released owner left a successful pending write")
        } catch {
            #expect(error is CancellationError)
        }
        #expect(store.values.isEmpty)
    }
}

private enum ApplicationWriteFailure: Error, Equatable { case persistence }

@MainActor
private final class ApplicationWriteCompletions {
    var values: [String] = []
}

@MainActor
private final class ApplicationWriteScreen {
    private let writes: ApplicationWriteTasks
    init(writes: ApplicationWriteTasks) { self.writes = writes }
    func accept(_ operation: @escaping @MainActor () async throws -> Void) -> Task<Void, Error> {
        writes.launch(operation)
    }
}

@MainActor
private final class ApplicationWriteGate {
    private var isOpen = false
    private var entered = false
    private var waiting: [CheckedContinuation<Void, Never>] = []
    private var entering: [CheckedContinuation<Void, Never>] = []

    func wait() async {
        entered = true
        entering.forEach { $0.resume() }
        entering.removeAll()
        if !isOpen { await withCheckedContinuation { waiting.append($0) } }
    }

    func waitUntilEntered() async {
        if !entered { await withCheckedContinuation { entering.append($0) } }
    }

    func open() {
        isOpen = true
        waiting.forEach { $0.resume() }
        waiting.removeAll()
    }
}
