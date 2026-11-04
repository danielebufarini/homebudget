@preconcurrency import ComposeApp
import Foundation
import Observation

struct StartupRestorePreview: Sendable {
    let categoriesCount: Int
    let expensesCount: Int
    let incomesCount: Int
}

struct PendingStartupRestore: Sendable {
    let text: String
    let preview: StartupRestorePreview
}

@MainActor
protocol StartupRestoreControlling {
    func isRestoreTargetEmpty() async throws -> Bool
    func prepareRestore(text: String) async throws -> StartupRestorePreview?
    func restoreBackup(text: String) async throws -> Bool
}

protocol StartupRestoreMarkerStoring: Sendable {
    func exists() async throws -> Bool
    func markCompleted() async throws
}

@MainActor
struct LiveStartupRestoreController: StartupRestoreControlling {
    private let controller = IosBackupRestoreController()

    func isRestoreTargetEmpty() async throws -> Bool {
        try await controller.isRestoreTargetEmpty().isSuccess
    }

    func prepareRestore(text: String) async throws -> StartupRestorePreview? {
        try await controller.prepareRestore(text: text).counters.map {
            StartupRestorePreview(categoriesCount: Int($0.categoriesCount), expensesCount: Int($0.expensesCount), incomesCount: Int($0.incomesCount))
        }
    }

    func restoreBackup(text: String) async throws -> Bool {
        try await controller.restoreBackup(text: text).counters != nil
    }
}

struct StartupRestoreFileMarkerStore: StartupRestoreMarkerStoring {
    private let markerURL: @Sendable () -> URL?

    init(markerURL: @escaping @Sendable () -> URL? = Self.liveMarkerURL) {
        self.markerURL = markerURL
    }

    func exists() async throws -> Bool {
        try await FoundationFileIO.shared.run {
            guard let url = markerURL() else { return false }
            return FileManager.default.fileExists(atPath: url.path)
        }
    }

    func markCompleted() async throws {
        try await FoundationFileIO.shared.run {
            guard let url = markerURL() else { return }
            try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
            try Data("completed".utf8).write(to: url, options: .atomic)
        }
    }

    private static func liveMarkerURL() -> URL? {
        FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first?
            .appendingPathComponent("startup-restore/completed.marker", isDirectory: false)
    }
}

/// App-owned startup orchestration. Dependencies expose the platform awaits so cancellation and
/// late provider results can be exercised independently of an iCloud account and scheduling.
@MainActor
@Observable
final class StartupRestoreCoordinator {
    private(set) var isReady = false
    var pendingRestore: PendingStartupRestore?
    @ObservationIgnored private(set) var actionTask: Task<Void, Never>?
    @ObservationIgnored private var didStart = false
    private let controller: any StartupRestoreControlling
    private let archive: any CloudBackupArchiving
    private let marker: any StartupRestoreMarkerStoring
    private let onRestored: @MainActor () -> Void

    init(
        controller: any StartupRestoreControlling = LiveStartupRestoreController(),
        archive: any CloudBackupArchiving = ICloudBackupStore.archive,
        marker: any StartupRestoreMarkerStoring = StartupRestoreFileMarkerStore(),
        onRestored: @escaping @MainActor () -> Void = { SpesifyWidgetSummaryRefresher.shared.refresh() }
    ) {
        self.controller = controller
        self.archive = archive
        self.marker = marker
        self.onRestored = onRestored
    }

    func prepareIfNeeded() async {
        guard !didStart, actionTask == nil else { return }
        didStart = true
        var completed = false
        defer {
            if completed && !Task.isCancelled {
                if pendingRestore == nil { isReady = true }
            } else {
                // A view-owned probe may be cancelled or return a stale platform result. Neither
                // event is an explicit skip/restore decision, and a subsequent appearance can retry.
                didStart = false
                pendingRestore = nil
            }
        }

        do {
            try Task.checkCancellation()
            let marked = try await marker.exists()
            try Task.checkCancellation()
            if marked { completed = true; return }

            let empty = try await controller.isRestoreTargetEmpty()
            try Task.checkCancellation()
            if !empty {
                try await marker.markCompleted()
                try Task.checkCancellation()
                completed = true
                return
            }

            let text = try await archive.readBackup()
            try Task.checkCancellation()
            let preview = try await controller.prepareRestore(text: text)
            try Task.checkCancellation()
            if let preview { pendingRestore = PendingStartupRestore(text: text, preview: preview) }
            completed = true
        } catch is CancellationError {
            // Leave startup uncompleted and retryable; never turn cancellation into "not empty".
        } catch {
            // Preserve offline/error startup availability. No marker is written on an unsuccessful
            // probe or cloud read, so a later app launch can still offer a restore.
            completed = !Task.isCancelled
        }
    }

    func confirmRestore(_ pending: PendingStartupRestore) {
        guard actionTask == nil, pendingRestore?.text == pending.text else { return }
        pendingRestore = nil
        // This task belongs to the app coordinator, not to the presenting view's .task lifetime.
        actionTask = Task {
            defer { actionTask = nil }
            do {
                try Task.checkCancellation()
                let restored = try await controller.restoreBackup(text: pending.text)
                try Task.checkCancellation()
                if restored {
                    onRestored()
                    try await marker.markCompleted()
                    try Task.checkCancellation()
                }
                isReady = true
            } catch is CancellationError {
                pendingRestore = pending
            } catch {
                if Task.isCancelled { pendingRestore = pending }
                else { isReady = true }
            }
        }
    }

    func skipRestore() {
        guard actionTask == nil, let pending = pendingRestore else { return }
        pendingRestore = nil
        actionTask = Task {
            defer { actionTask = nil }
            do {
                try Task.checkCancellation()
                try await marker.markCompleted()
                try Task.checkCancellation()
                isReady = true
            } catch is CancellationError {
                pendingRestore = pending
            } catch {
                if Task.isCancelled { pendingRestore = pending }
                else { isReady = true }
            }
        }
    }
}
