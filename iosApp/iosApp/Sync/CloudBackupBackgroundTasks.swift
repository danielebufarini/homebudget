@preconcurrency import ComposeApp
import BackgroundTasks
import Foundation

enum CloudBackupBackgroundTasks {
    static let identifier = "it.danielebufarini.spesify.cloudsync"

    /// BGTask is an Objective-C system lease without Sendable conformance. The immutable reference
    /// is transferred only through this adapter; the job reserves completion once under its mutex,
    /// and the sole setTaskCompleted call runs on MainActor. No mutable application state crosses it.
    private final class BackgroundTaskCompletion: @unchecked Sendable {
        private let task: BGProcessingTask

        init(task: BGProcessingTask) { self.task = task }

        func complete(success: Bool) async {
            await MainActor.run { task.setTaskCompleted(success: success) }
        }
    }

    private struct ExportError: LocalizedError {
        let message: String?
        var errorDescription: String? { message }
    }

    static func register() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: identifier, using: nil) { task in
            guard let processingTask = task as? BGProcessingTask else {
                task.setTaskCompleted(success: false)
                return
            }
            handle(processingTask)
        }
    }

    static func schedule() {
        let request = BGProcessingTaskRequest(identifier: identifier)
        request.requiresExternalPower = true
        request.requiresNetworkConnectivity = true

        do {
            try BGTaskScheduler.shared.submit(request)
        } catch {
            #if DEBUG
            print("Unable to schedule cloud backup task: \(error)")
            #endif
        }
    }

    private static func handle(_ task: BGProcessingTask) {
        schedule()
        let completion = BackgroundTaskCompletion(task: task)
        let job = CloudBackupJob(
            export: {
                let result = try await IosBackupExportController().exportBackup()
                guard result.errorMessage == nil, let content = result.content else {
                    throw ExportError(message: result.errorMessage)
                }
                return content
            },
            archive: ICloudBackupStore.archive,
            reportCompletion: { success in await completion.complete(success: success) }
        )
        // Install expiry before starting work. A pre-start expiry wins the job's terminal state
        // and prevents export; the worker is owned before it can pass its first active-state check.
        task.expirationHandler = { job.expire() }
        job.start()
    }
}
