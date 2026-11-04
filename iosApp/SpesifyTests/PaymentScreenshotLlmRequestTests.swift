import Testing
import Synchronization
@testable import Spesify

struct PaymentScreenshotLlmRequestTests {
    @Test
    func completionIsDeliveredAtMostOnce() {
        let results = Mutex<[String]>([])
        let request = PaymentScreenshotLlmRequest { value in results.withLock { $0.append(value) } }
        request.finish("first")
        request.finish("second")
        request.cancel()
        #expect(results.withLock { $0 } == ["first"])
    }

    @Test
    func cancellationIgnoresLateCompletionAndCancelsLateAttachedTask() async {
        let results = Mutex<[String]>([])
        let request = PaymentScreenshotLlmRequest { value in results.withLock { $0.append(value) } }
        request.cancel()
        let task = Task<Void, Never> { _ = try? await Task.sleep(for: .seconds(60)) }
        request.attach(task)
        await task.value
        #expect(task.isCancelled)
        request.finish("late")
        #expect(results.withLock { $0.isEmpty })
    }

    @Test
    func concurrentCompletionAndCancellationHaveOneWinner() async {
        let results = Mutex<[String]>([])
        let request = PaymentScreenshotLlmRequest { value in results.withLock { $0.append(value) } }
        await withTaskGroup(of: Void.self) { group in
            for index in 0..<64 {
                group.addTask {
                    if index.isMultiple(of: 2) { request.cancel() } else { request.finish("value") }
                }
            }
        }
        #expect(results.withLock { $0.count } <= 1)
    }

    @Test
    func providerHandleCancelsItsActualTaskAndSuppressesCompletion() async {
        let started = AsyncStream<Void>.makeStream()
        let cancelled = AsyncStream<Void>.makeStream()
        let results = Mutex<[String]>([])
        let provider = FoundationModelsPaymentScreenshotLlmProvider { _, _ in
            started.continuation.yield(())
            do {
                try await Task.sleep(for: .seconds(60))
                return "late"
            } catch {
                cancelled.continuation.yield(())
                throw error
            }
        }
        let handle = provider.interpret(text: "payment", moneyCandidates: []) { value in
            results.withLock { $0.append(value) }
        }
        var startEvents = started.stream.makeAsyncIterator()
        await startEvents.next()
        handle.cancel()
        var cancelEvents = cancelled.stream.makeAsyncIterator()
        await cancelEvents.next()
        #expect(results.withLock { $0.isEmpty })
    }
}
