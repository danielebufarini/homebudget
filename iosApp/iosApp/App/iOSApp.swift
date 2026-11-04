@preconcurrency import ComposeApp
import BackgroundTasks
import SwiftUI

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppOrientationController.self) private var orientationController
    @Environment(\.scenePhase) private var scenePhase
    @State private var startupRestore = StartupRestoreCoordinator()
    @State private var pendingOpenURL: PendingOpenURL?

    init() {
        IosLocalLlmExpenseTextInterpreterRegistry.shared.installProvider(
            provider: FoundationModelsPaymentScreenshotLlmProvider()
        )
        CloudBackupBackgroundTasks.register()
        CloudBackupBackgroundTasks.schedule()
    }

    var body: some Scene {
        WindowGroup {
            Group {
                if startupRestore.isReady {
                    ContentView(pendingOpenURL: $pendingOpenURL)
                } else {
                    ProgressView()
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            }
            .task {
                await startupRestore.prepareIfNeeded()
            }
            .alert(
                appLocalized("Restore Backup"),
                isPresented: Binding(
                    get: { startupRestore.pendingRestore != nil },
                    set: { isPresented in
                        if !isPresented && startupRestore.isReady {
                            startupRestore.pendingRestore = nil
                        }
                    }
                ),
                presenting: startupRestore.pendingRestore
            ) { pendingRestore in
                Button(appLocalized("Cancel"), role: .cancel) {
                    startupRestore.skipRestore()
                }
                Button(appLocalized("Restore")) {
                    startupRestore.confirmRestore(pendingRestore)
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
            .onOpenURL { url in
                pendingOpenURL = PendingOpenURL(url: url)
            }
        }
        .onChange(of: scenePhase) { _, newPhase in
            if newPhase == .background {
                CloudBackupBackgroundTasks.schedule()
                SpesifyWidgetSummaryRefresher.shared.refresh()
            }
        }
    }

}
