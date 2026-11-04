import Foundation
import Synchronization

protocol CloudBackupArchiving: Sendable {
    func writeBackup(text: String) async throws
    func readBackup() async throws -> String
}

/// Owns one export/write operation and the bounded finalization of its background lease.
/// Expiration and normal completion atomically reserve the same terminal state.
final class CloudBackupJob: Sendable {
    private struct State {
        var started = false
        var completed = false
        var worker: Task<Void, Never>?
        var finalizer: Task<Void, Never>?
    }

    private let state = Mutex(State())
    private let export: @Sendable () async throws -> String
    private let archive: any CloudBackupArchiving
    private let reportCompletion: @Sendable (Bool) async -> Void

    init(
        export: @escaping @Sendable () async throws -> String,
        archive: any CloudBackupArchiving,
        reportCompletion: @escaping @Sendable (Bool) async -> Void
    ) {
        self.export = export
        self.archive = archive
        self.reportCompletion = reportCompletion
    }

    @discardableResult
    func start() -> Task<Void, Never>? {
        state.withLock { state in
            guard !state.started, !state.completed else { return nil }
            state.started = true
            let worker = Task { await performBackup() }
            state.worker = worker
            return worker
        }
    }

    func expire() {
        let worker = state.withLock { state -> Task<Void, Never>? in
            guard !state.completed else { return nil }
            state.completed = true
            let worker = state.worker
            state.worker = nil
            // The job owns this short task. Reporting expiration must not wait for synchronous
            // Foundation I/O already in progress to return to the cancelled worker.
            state.finalizer = Task {
                await withTaskCancellationShield {
                    await reportCompletion(false)
                }
                self.state.withLock { $0.finalizer = nil }
            }
            return worker
        }
        worker?.cancel()
    }

    private func performBackup() async {
        var success = false
        defer {
            // A shield masks Task.isCancelled, so determine the outcome before installing it.
            let finalSuccess = success && !Task.isCancelled
            await withTaskCancellationShield {
                await finish(success: finalSuccess)
            }
        }

        // start() installs ownership under this same mutex before work can begin.
        guard state.withLock({ !$0.completed }) else { return }

        do {
            try checkActive()
            let content = try await export()
            try checkActive()
            try await archive.writeBackup(text: content)
            try checkActive()
            success = true
        } catch {
            // Cancellation and infrastructure errors both fail this background attempt. The
            // deferred lease completion runs on every exit, including cancellation without expiry.
        }
    }

    private func checkActive() throws {
        try Task.checkCancellation()
        guard state.withLock({ !$0.completed }) else { throw CancellationError() }
    }

    private func finish(success: Bool) async {
        let shouldReport = state.withLock { state in
            guard !state.completed else { return false }
            state.completed = true
            state.worker = nil
            return true
        }
        if shouldReport { await reportCompletion(success) }
    }
}
