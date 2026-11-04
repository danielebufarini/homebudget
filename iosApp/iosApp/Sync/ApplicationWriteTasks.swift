import Foundation

/// Owns accepted writes independently of the screen that requested them. Observation tasks still
/// belong to their views; leaving a view does not cancel a write already handed to this owner.
@MainActor
final class ApplicationWriteTasks {
    static let shared = ApplicationWriteTasks()

    private struct OwnedTask: Sendable {
        let cancel: @Sendable () -> Void
    }

    private var tasks: [UUID: OwnedTask] = [:]
    var pendingCount: Int { tasks.count }

    deinit { tasks.values.forEach { $0.cancel() } }

    @discardableResult
    func launch(_ operation: @escaping @MainActor () async throws -> Void) -> Task<Void, Error> {
        perform(operation: operation)
    }

    @discardableResult
    func perform<Value: Sendable>(
        operation: @escaping @MainActor () async throws -> Value
    ) -> Task<Value, Error> {
        let id = UUID()
        let task = Task<Value, Error> { [weak self] in
            defer { self?.tasks[id] = nil }
            try Task.checkCancellation()
            let value = try await operation()
            try Task.checkCancellation()
            return value
        }
        // Erase only cancellation, preserving the typed task/result returned to its caller.
        tasks[id] = OwnedTask(cancel: { task.cancel() })
        return task
    }
}
