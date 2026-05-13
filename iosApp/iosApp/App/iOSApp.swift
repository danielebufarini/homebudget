@preconcurrency import ComposeApp
import BackgroundTasks
import SwiftUI

private struct StartupRestorePreview: Sendable {
    let categoriesCount: Int
    let expensesCount: Int
    let incomesCount: Int
}

private struct PendingStartupRestore {
    let text: String
    let preview: StartupRestorePreview
}

@main
struct iOSApp: App {
    @Environment(\.scenePhase) private var scenePhase
    @State private var didStartStartupRestore = false
    @State private var isStartupReady = false
    @State private var pendingStartupRestore: PendingStartupRestore?

    init() {
        CloudBackupBackgroundTasks.register()
        CloudBackupBackgroundTasks.schedule()
    }

    var body: some Scene {
        WindowGroup {
            Group {
                if isStartupReady {
                    ContentView()
                } else {
                    ProgressView()
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            }
            .task {
                await completeStartupRestoreIfNeeded()
            }
            .alert(
                appLocalized("Restore Backup"),
                isPresented: Binding(
                    get: { pendingStartupRestore != nil },
                    set: { isPresented in
                        if !isPresented && isStartupReady {
                            pendingStartupRestore = nil
                        }
                    }
                ),
                presenting: pendingStartupRestore
            ) { pendingRestore in
                Button(appLocalized("Cancel"), role: .cancel) {
                    skipStartupRestore()
                }
                Button(appLocalized("Restore")) {
                    Task {
                        await confirmStartupRestore(pendingRestore)
                    }
                }
            } message: { pendingRestore in
                Text(
                    appLocalized(
                        "This will replace the current data with %1$d categories, %2$d expenses, and %3$d incomes from the backup file.",
                        pendingRestore.preview.categoriesCount,
                        pendingRestore.preview.expensesCount,
                        pendingRestore.preview.incomesCount
                    )
                )
            }
        }
        .onChange(of: scenePhase) { _, newPhase in
            if newPhase == .background {
                CloudBackupBackgroundTasks.schedule()
                HomeBudgetWidgetSummaryRefresher.shared.refresh()
            }
        }
    }

    @MainActor
    private func completeStartupRestoreIfNeeded() async {
        guard !didStartStartupRestore else {
            return
        }

        didStartStartupRestore = true
        defer {
            if pendingStartupRestore == nil {
                isStartupReady = true
            }
        }

        if startupRestoreMarkerExists() {
            return
        }

        let controller = IosBackupRestoreController()
        defer {
            controller.dispose()
        }

        let isRestoreTargetEmpty = await withCheckedContinuation { continuation in
            controller.isRestoreTargetEmpty { isEmpty in
                continuation.resume(returning: Bool(truncating: isEmpty))
            }
        }

        guard isRestoreTargetEmpty else {
            markStartupRestoreCompleted()
            return
        }

        do {
            let text = try await ICloudBackupStore.readBackup()
            let preview = await withCheckedContinuation { continuation in
                controller.prepareRestore(text: text) { preview, _ in
                    continuation.resume(
                        returning: preview.map {
                            StartupRestorePreview(
                                categoriesCount: Int($0.categoriesCount),
                                expensesCount: Int($0.expensesCount),
                                incomesCount: Int($0.incomesCount)
                            )
                        }
                    )
                }
            }

            if let preview {
                pendingStartupRestore = PendingStartupRestore(
                    text: text,
                    preview: preview
                )
            }
        } catch {
            return
        }
    }

    @MainActor
    private func confirmStartupRestore(_ pendingRestore: PendingStartupRestore) async {
        pendingStartupRestore = nil
        let controller = IosBackupRestoreController()
        defer {
            controller.dispose()
        }

        let restored = await withCheckedContinuation { continuation in
            controller.restoreBackup(text: pendingRestore.text) { result, _ in
                continuation.resume(returning: result != nil)
            }
        }

        if restored {
            markStartupRestoreCompleted()
            HomeBudgetWidgetSummaryRefresher.shared.refresh()
        }
        isStartupReady = true
    }

    @MainActor
    private func skipStartupRestore() {
        pendingStartupRestore = nil
        markStartupRestoreCompleted()
        isStartupReady = true
    }

    private func startupRestoreMarkerExists() -> Bool {
        guard let url = startupRestoreMarkerURL() else {
            return false
        }
        return FileManager.default.fileExists(atPath: url.path)
    }

    private func markStartupRestoreCompleted() {
        guard let url = startupRestoreMarkerURL() else {
            return
        }

        let directory = url.deletingLastPathComponent()
        try? FileManager.default.createDirectory(
            at: directory,
            withIntermediateDirectories: true
        )
        FileManager.default.createFile(
            atPath: url.path,
            contents: Data("completed".utf8)
        )
    }

    private func startupRestoreMarkerURL() -> URL? {
        return FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)
            .first?
            .appendingPathComponent("startup-restore/completed.marker", isDirectory: false)
    }
}
