import Foundation
import Synchronization

/// Foundation's file APIs are synchronous. Keep them on one dedicated queue; cancellation can
/// prevent a queued operation starting and is rechecked after in-flight, non-preemptible I/O.
final class FoundationFileIO: Sendable {
    static let shared = FoundationFileIO()
    private let queue = DispatchQueue(label: "it.danielebufarini.spesify.foundation.files", qos: .utility)

    private final class Cancellation: Sendable {
        let isCancelled = Mutex(false)
    }

    func run<Value: Sendable>(_ operation: @escaping @Sendable () throws -> Value) async throws -> Value {
        try Task.checkCancellation()
        let cancellation = Cancellation()
        let value: Value = try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { continuation in
                queue.async {
                    do {
                        guard !cancellation.isCancelled.withLock({ $0 }) else { throw CancellationError() }
                        continuation.resume(returning: try operation())
                    } catch {
                        continuation.resume(throwing: error)
                    }
                }
            }
        } onCancel: {
            cancellation.isCancelled.withLock { $0 = true }
        }
        try Task.checkCancellation()
        return value
    }

    func runSync<Value>(_ operation: () throws -> Value) rethrows -> Value {
        try queue.sync(execute: operation)
    }
}
