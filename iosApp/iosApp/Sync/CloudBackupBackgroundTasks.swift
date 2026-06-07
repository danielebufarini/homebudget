@preconcurrency import ComposeApp
import BackgroundTasks
import Foundation

enum CloudBackupBackgroundTasks {
    static let identifier = "it.danielebufarini.spesify.cloudsync"

    private final class CancellationState: @unchecked Sendable {
        private let lock = NSLock()
        private var cancelled = false

        func cancel() {
            lock.lock()
            cancelled = true
            lock.unlock()
        }

        var isCancelled: Bool {
            lock.lock()
            let value = cancelled
            lock.unlock()
            return value
        }
    }

    private final class BackgroundTaskCompletion: @unchecked Sendable {
        private let lock = NSLock()
        private let task: BGProcessingTask
        private var didComplete = false

        init(task: BGProcessingTask) {
            self.task = task
        }

        func complete(success: Bool) {
            lock.lock()
            guard !didComplete else {
                lock.unlock()
                return
            }
            didComplete = true
            lock.unlock()

            task.setTaskCompleted(success: success)
        }
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
        let cancellationState = CancellationState()
        let completion = BackgroundTaskCompletion(task: task)
        let backupTask = Task {
            let success = await performCloudBackup(cancellationState: cancellationState)
            guard !Task.isCancelled else {
                return
            }
            completion.complete(success: success)
        }

        task.expirationHandler = {
            cancellationState.cancel()
            backupTask.cancel()
            completion.complete(success: false)
        }
    }

    private static func performCloudBackup(cancellationState: CancellationState) async -> Bool {
        guard !cancellationState.isCancelled else {
            return false
        }

        let controller = IosBackupExportController()
        guard !cancellationState.isCancelled,
              !Task.isCancelled,
              let result = try? await controller.exportBackup(),
              result.errorMessage == nil,
              let content = result.content else {
            return false
        }

        guard !cancellationState.isCancelled, !Task.isCancelled else {
            return false
        }

        do {
            try ICloudBackupStore.writeBackupSync(text: content)
            return !cancellationState.isCancelled && !Task.isCancelled
        } catch {
            return false
        }
    }
}
