import Foundation
import Testing
@testable import Spesify

@MainActor
struct StartupRestoreCoordinatorTests {
    @Test
    func cancelledEmptinessProbeDoesNotSuppressFutureRestoreAndCanBeRetried() async throws {
        let fixture = try StartupMarkerFixture()
        defer { fixture.remove() }
        let controller = StartupControllerStub()
        controller.probeError = CancellationError()
        let archive = StartupArchiveStub()
        let coordinator = StartupRestoreCoordinator(controller: controller, archive: archive, marker: fixture.store, onRestored: {})

        await coordinator.prepareIfNeeded()
        #expect(!fixture.exists)
        #expect(!coordinator.isReady)
        #expect(coordinator.pendingRestore == nil)
        controller.probeError = nil
        await coordinator.prepareIfNeeded()
        #expect(controller.probeCalls == 2)
        #expect(coordinator.pendingRestore?.text == archive.content)
        #expect(!fixture.exists)
    }

    @Test
    func cancelledViewCannotCompleteStartupFromALateNonemptyProbe() async throws {
        let fixture = try StartupMarkerFixture()
        defer { fixture.remove() }
        let controller = StartupControllerStub()
        controller.probeGate = StartupTestGate()
        controller.targetIsEmpty = false
        let coordinator = StartupRestoreCoordinator(controller: controller, archive: StartupArchiveStub(), marker: fixture.store, onRestored: {})
        let loading = Task { await coordinator.prepareIfNeeded() }
        await controller.probeGate?.waitUntilEntered()
        loading.cancel()
        controller.probeGate?.open()
        await loading.value
        #expect(!fixture.exists)
        #expect(!coordinator.isReady)
        #expect(coordinator.pendingRestore == nil)
    }

    @Test
    func cancelledPreviewCannotPublishAPromptAndCanBeRetried() async throws {
        let fixture = try StartupMarkerFixture()
        defer { fixture.remove() }
        let controller = StartupControllerStub()
        controller.previewGate = StartupTestGate()
        let archive = StartupArchiveStub()
        let coordinator = StartupRestoreCoordinator(controller: controller, archive: archive, marker: fixture.store, onRestored: {})
        let loading = Task { await coordinator.prepareIfNeeded() }
        await controller.previewGate?.waitUntilEntered()
        loading.cancel()
        controller.previewGate?.open()
        await loading.value
        #expect(coordinator.pendingRestore == nil)
        #expect(!coordinator.isReady)
        #expect(!fixture.exists)
        await coordinator.prepareIfNeeded()
        #expect(controller.probeCalls == 2)
        #expect(coordinator.pendingRestore?.text == archive.content)
    }

    @Test
    func existingMarkerMakesStartupReadyWithoutProbingOrReadingCloud() async throws {
        let fixture = try StartupMarkerFixture()
        defer { fixture.remove() }
        try await fixture.store.markCompleted()
        let controller = StartupControllerStub()
        let archive = StartupArchiveStub()
        let coordinator = StartupRestoreCoordinator(controller: controller, archive: archive, marker: fixture.store, onRestored: {})
        await coordinator.prepareIfNeeded()
        #expect(coordinator.isReady)
        #expect(controller.probeCalls == 0)
        #expect(archive.readCalls == 0)
    }

    @Test
    func confirmedNonemptyTargetMarksStartupCompleteWithoutReadingCloud() async throws {
        let fixture = try StartupMarkerFixture()
        defer { fixture.remove() }
        let controller = StartupControllerStub()
        controller.targetIsEmpty = false
        let archive = StartupArchiveStub()
        let coordinator = StartupRestoreCoordinator(controller: controller, archive: archive, marker: fixture.store, onRestored: {})
        await coordinator.prepareIfNeeded()
        #expect(coordinator.isReady)
        #expect(fixture.exists)
        #expect(archive.readCalls == 0)
    }

    @Test
    func unavailableCloudContinuesStartupWithoutWritingCompletionMarker() async throws {
        let fixture = try StartupMarkerFixture()
        defer { fixture.remove() }
        let archive = StartupArchiveStub()
        archive.error = ICloudBackupStoreError.unavailable
        let coordinator = StartupRestoreCoordinator(controller: StartupControllerStub(), archive: archive, marker: fixture.store, onRestored: {})
        await coordinator.prepareIfNeeded()
        #expect(coordinator.isReady)
        #expect(coordinator.pendingRestore == nil)
        #expect(!fixture.exists)
    }

    @Test
    func validPreviewWaitsForExplicitSkipBeforeWritingTheMarker() async throws {
        let fixture = try StartupMarkerFixture()
        defer { fixture.remove() }
        let coordinator = StartupRestoreCoordinator(controller: StartupControllerStub(), archive: StartupArchiveStub(), marker: fixture.store, onRestored: {})
        await coordinator.prepareIfNeeded()
        #expect(!coordinator.isReady)
        #expect(coordinator.pendingRestore?.preview.categoriesCount == 2)
        #expect(!fixture.exists)
        coordinator.skipRestore()
        await coordinator.actionTask?.value
        #expect(coordinator.isReady)
        #expect(coordinator.pendingRestore == nil)
        #expect(try String(contentsOf: fixture.file, encoding: .utf8) == "completed")
    }

    @Test
    func confirmedRestoreBelongsToAppCoordinatorAndSurvivesViewTaskCancellation() async throws {
        let fixture = try StartupMarkerFixture()
        defer { fixture.remove() }
        let controller = StartupControllerStub()
        controller.restoreGate = StartupTestGate()
        var refreshes = 0
        let coordinator = StartupRestoreCoordinator(controller: controller, archive: StartupArchiveStub(), marker: fixture.store, onRestored: { refreshes += 1 })
        await coordinator.prepareIfNeeded()
        let pending = try #require(coordinator.pendingRestore)
        let viewLifetime = AsyncStream<Void>.makeStream()
        let viewTask = Task {
            coordinator.confirmRestore(pending)
            var iterator = viewLifetime.stream.makeAsyncIterator()
            await iterator.next()
        }
        await controller.restoreGate?.waitUntilEntered()
        let restoring = try #require(coordinator.actionTask)
        viewTask.cancel()
        await viewTask.value
        controller.restoreGate?.open()
        await restoring.value
        #expect(controller.restoreCalls == 1)
        #expect(coordinator.isReady)
        #expect(fixture.exists)
        #expect(refreshes == 1)
    }

    @Test
    func cancelledConfirmationDoesNotPretendStartupCompletedAndCanBeRetried() async throws {
        let fixture = try StartupMarkerFixture()
        defer { fixture.remove() }
        let controller = StartupControllerStub()
        controller.restoreError = CancellationError()
        var refreshes = 0
        let coordinator = StartupRestoreCoordinator(controller: controller, archive: StartupArchiveStub(), marker: fixture.store, onRestored: { refreshes += 1 })
        await coordinator.prepareIfNeeded()
        let pending = try #require(coordinator.pendingRestore)
        coordinator.confirmRestore(pending)
        await coordinator.actionTask?.value
        #expect(!coordinator.isReady)
        #expect(coordinator.pendingRestore != nil)
        #expect(!fixture.exists)
        #expect(refreshes == 0)
        controller.restoreError = nil
        coordinator.confirmRestore(pending)
        await coordinator.actionTask?.value
        #expect(coordinator.isReady)
        #expect(fixture.exists)
        #expect(refreshes == 1)
    }
}

private struct StartupMarkerFixture {
    let directory: URL
    let file: URL
    init() throws {
        directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        file = directory.appendingPathComponent("startup-restore/completed.marker")
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    }
    var exists: Bool { FileManager.default.fileExists(atPath: file.path) }
    var store: StartupRestoreFileMarkerStore {
        let file = file
        return StartupRestoreFileMarkerStore(markerURL: { file })
    }
    func remove() { try? FileManager.default.removeItem(at: directory) }
}

// Gate results deliberately arrive after cancellation, matching platform calls that do not
// cooperatively stop. Assertions exercise the production coordinator and actual marker files.
@MainActor
private final class StartupTestGate {
    private var opened = false
    private var entered = false
    private var entryWaiters: [CheckedContinuation<Void, Never>] = []
    private var waiters: [CheckedContinuation<Void, Never>] = []
    func wait() async {
        entered = true
        entryWaiters.forEach { $0.resume() }
        entryWaiters.removeAll()
        if !opened { await withCheckedContinuation { waiters.append($0) } }
    }
    func waitUntilEntered() async {
        if !entered { await withCheckedContinuation { entryWaiters.append($0) } }
    }
    func open() {
        opened = true
        waiters.forEach { $0.resume() }
        waiters.removeAll()
    }
}

@MainActor
private final class StartupControllerStub: StartupRestoreControlling {
    var targetIsEmpty = true
    var probeError: (any Error)?
    var restoreError: (any Error)?
    var probeGate: StartupTestGate?
    var previewGate: StartupTestGate?
    var restoreGate: StartupTestGate?
    var probeCalls = 0
    var restoreCalls = 0
    func isRestoreTargetEmpty() async throws -> Bool {
        probeCalls += 1
        await probeGate?.wait()
        if let probeError { throw probeError }
        return targetIsEmpty
    }
    func prepareRestore(text: String) async throws -> StartupRestorePreview? {
        await previewGate?.wait()
        return StartupRestorePreview(categoriesCount: 2, expensesCount: 3, incomesCount: 4)
    }
    func restoreBackup(text: String) async throws -> Bool {
        restoreCalls += 1
        await restoreGate?.wait()
        try Task.checkCancellation()
        if let restoreError { throw restoreError }
        return true
    }
}

@MainActor
private final class StartupArchiveStub: CloudBackupArchiving {
    let content = "test-backup-content"
    var readCalls = 0
    var error: (any Error)?
    func readBackup() async throws -> String {
        readCalls += 1
        if let error { throw error }
        return content
    }
    func writeBackup(text: String) async throws {
        Issue.record("Startup restore must not write a cloud backup")
    }
}
