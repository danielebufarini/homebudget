@preconcurrency import ComposeApp
import BackgroundTasks
import Foundation

enum CloudBackupBackgroundTasks {
    static let identifier = "it.danielebufarini.homebudget.cloudsync"

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

        task.expirationHandler = {
            cancellationState.cancel()
        }

        let success = performCloudBackup(cancellationState: cancellationState)
        task.setTaskCompleted(success: success)
    }

    private static func performCloudBackup(cancellationState: CancellationState) -> Bool {
        guard !cancellationState.isCancelled else {
            return false
        }

        let controller = IosBackupExportController()
        defer {
            controller.dispose()
        }

        let semaphore = DispatchSemaphore(value: 0)
        var success = false

        controller.exportBackup { _, content, errorMessage in
            defer {
                semaphore.signal()
            }

            guard !cancellationState.isCancelled else {
                return
            }

            guard errorMessage == nil, let content else {
                return
            }

            do {
                try ICloudBackupStore.writeBackupSync(text: content)
                success = true
            } catch {
                success = false
            }
        }

        while semaphore.wait(timeout: .now() + .milliseconds(250)) == .timedOut {
            if cancellationState.isCancelled {
                return false
            }
        }

        return success
    }
}
