import Foundation
import Testing
@testable import Spesify

@MainActor
struct ApplicationWriteValueTests {
    @Test
    func typedResultIsPreservedAndItsHandleIsReleased() async throws {
        let writes = ApplicationWriteTasks()
        let gate = ValueWriteGate()
        let receipt = ValueWriteReceipt(importedCount: 3, description: "committed")
        let task = writes.perform {
            await gate.wait()
            return receipt
        }
        await gate.waitUntilEntered()
        #expect(writes.pendingCount == 1)
        gate.open()
        #expect(try await task.value == receipt)
        #expect(writes.pendingCount == 0)
    }

    @Test
    func differentResultTypesHaveIndependentOwnedHandles() async throws {
        let writes = ApplicationWriteTasks()
        let firstGate = ValueWriteGate()
        let secondGate = ValueWriteGate()
        let first = writes.perform { await firstGate.wait(); return "first" }
        let second = writes.perform { await secondGate.wait(); return 42 }
        await firstGate.waitUntilEntered()
        await secondGate.waitUntilEntered()
        #expect(writes.pendingCount == 2)
        secondGate.open()
        #expect(try await second.value == 42)
        #expect(writes.pendingCount == 1)
        firstGate.open()
        #expect(try await first.value == "first")
        #expect(writes.pendingCount == 0)
    }

    @Test
    func typedWriteRethrowsTheOriginalFailureAndCleansUp() async {
        let writes = ApplicationWriteTasks()
        let task: Task<ValueWriteReceipt, Error> = writes.perform {
            throw ValueWriteFailure.persistence
        }
        do {
            _ = try await task.value
            Issue.record("Failed write produced a receipt")
        } catch {
            #expect(error as? ValueWriteFailure == .persistence)
        }
        #expect(writes.pendingCount == 0)
    }

    @Test
    func explicitCancellationDoesNotReturnATypedReceipt() async {
        let writes = ApplicationWriteTasks()
        let started = AsyncStream<Void>.makeStream()
        let task = writes.perform {
            started.continuation.yield(())
            try await Task.sleep(for: .seconds(60))
            return ValueWriteReceipt(importedCount: 1, description: "unreachable")
        }
        var events = started.stream.makeAsyncIterator()
        await events.next()
        task.cancel()
        do {
            _ = try await task.value
            Issue.record("Cancelled write produced a receipt")
        } catch {
            #expect(error is CancellationError)
        }
        #expect(writes.pendingCount == 0)
    }

    @Test
    func cancellingTheClientAwaitDoesNotCancelAnAcceptedTypedWrite() async throws {
        let writes = ApplicationWriteTasks()
        let gate = ValueWriteGate()
        let receipt = ValueWriteReceipt(importedCount: 2, description: "accepted import")
        let task = writes.perform { await gate.wait(); return receipt }
        let client = Task { try await task.value }
        await gate.waitUntilEntered()
        client.cancel()
        #expect(!task.isCancelled)
        #expect(writes.pendingCount == 1)
        gate.open()
        #expect(try await task.value == receipt)
        #expect(writes.pendingCount == 0)
        // Task.value may still deliver the operation's result to a cancelled waiter. The client
        // must separately suppress UI publication; ownership must not cancel the accepted write.
        _ = try? await client.value
    }

    @Test
    func releasingApplicationOwnerCancelsItsTypedWork() async throws {
        var writes: ApplicationWriteTasks? = ApplicationWriteTasks()
        weak let releasedOwner = writes
        let started = AsyncStream<Void>.makeStream()
        let accepted = writes?.perform {
            started.continuation.yield(())
            try await Task.sleep(for: .seconds(60))
            return ValueWriteReceipt(importedCount: 1, description: "unreachable")
        }
        let task = try #require(accepted)
        var events = started.stream.makeAsyncIterator()
        await events.next()
        writes = nil
        #expect(releasedOwner == nil)
        #expect(task.isCancelled)
        do {
            _ = try await task.value
            Issue.record("Released application owner left typed work running")
        } catch {
            #expect(error is CancellationError)
        }
    }
}

private struct ValueWriteReceipt: Sendable, Equatable {
    let importedCount: Int
    let description: String
}

private enum ValueWriteFailure: Error, Equatable { case persistence }

@MainActor
private final class ValueWriteGate {
    private var entered = false
    private var opened = false
    private var entryWaiters: [CheckedContinuation<Void, Never>] = []
    private var waiters: [CheckedContinuation<Void, Never>] = []
    func wait() async {
        entered = true
        entryWaiters.forEach { $0.resume() }
        entryWaiters.removeAll()
        if !opened { await withCheckedContinuation { waiters.append($0) } }
    }
    func waitUntilEntered() async {
        if !entered { await withCheckedContinuation { entryWaiters.append($0) } }
    }
    func open() {
        opened = true
        waiters.forEach { $0.resume() }
        waiters.removeAll()
    }
}
