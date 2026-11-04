import Foundation
import Synchronization
import Testing
@testable import Spesify

private enum BackupTestError: Error { case exportFailed }

private final class BackupCompletionLog: Sendable {
    let values = Mutex<[Bool]>([])
    let cancellationWasMasked = Mutex(false)
    let events = AsyncStream<Void>.makeStream()

    func complete(_ success: Bool) async {
        cancellationWasMasked.withLock { $0 = !Task.isCancelled }
        // This cancellation-aware suspension belongs to finalization, so cancellation of the
        // worker must not abort it. The production adapter awaits MainActor to close its BG lease.
        do { try await Task.sleep(for: .zero) } catch {
            cancellationWasMasked.withLock { $0 = false }
        }
        values.withLock { $0.append(success) }
        events.continuation.yield(())
    }

    func wait() async {
        var iterator = events.stream.makeAsyncIterator()
        await iterator.next()
    }
}

private struct BackupTestFile {
    let directory: URL
    let file: URL

    init() throws {
        directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        file = directory.appendingPathComponent("spesify-backup.json")
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    }

    func remove() { try? FileManager.default.removeItem(at: directory) }

    var archive: ICloudBackupArchive {
        let file = file
        return ICloudBackupArchive(resolveBackupURL: { file }, isUbiquitousItem: { _ in false })
    }
}

private actor HeldBackupArchive: CloudBackupArchiving {
    nonisolated let started = AsyncStream<Void>.makeStream()
    private var continuation: CheckedContinuation<Void, Never>?
    private(set) var didReturnFromWrite = false

    func writeBackup(text: String) async throws {
        await withCheckedContinuation { continuation in
            self.continuation = continuation
            started.continuation.yield(())
        }
        didReturnFromWrite = true
    }

    func releaseWrite() {
        continuation?.resume()
        continuation = nil
    }

    func readBackup() async throws -> String { throw BackupTestError.exportFailed }
}

struct CloudBackupJobTests {
    @Test
    func successPersistsExportedBytesAndCompletesOnce() async throws {
        let fixture = try BackupTestFile()
        defer { fixture.remove() }
        let completion = BackupCompletionLog()
        let exportCalls = Mutex(0)
        let content = #"{"version":4,"note":"caffè ☕","amount":1200}"#
        let job = CloudBackupJob(export: {
            exportCalls.withLock { $0 += 1 }
            return content
        }, archive: fixture.archive, reportCompletion: completion.complete)

        let worker = try #require(job.start())
        #expect(job.start() == nil)
        await worker.value
        job.expire()
        #expect(exportCalls.withLock { $0 } == 1)
        #expect(try Data(contentsOf: fixture.file) == Data(content.utf8))
        #expect(completion.values.withLock { $0 } == [true])
    }

    @Test
    func exportFailureDoesNotCreateBackupAndCompletesOnce() async throws {
        let fixture = try BackupTestFile()
        defer { fixture.remove() }
        let completion = BackupCompletionLog()
        let job = CloudBackupJob(export: { throw BackupTestError.exportFailed },
                                 archive: fixture.archive, reportCompletion: completion.complete)
        await job.start()?.value
        job.expire()
        #expect(!FileManager.default.fileExists(atPath: fixture.file.path))
        #expect(completion.values.withLock { $0 } == [false])
    }

    @Test
    func writeFailureIsReportedAsFailureExactlyOnce() async throws {
        let fixture = try BackupTestFile()
        defer { fixture.remove() }
        let missingParentFile = fixture.directory.appendingPathComponent("missing/backup.json")
        let archive = ICloudBackupArchive(resolveBackupURL: { missingParentFile })
        let completion = BackupCompletionLog()
        let job = CloudBackupJob(export: { "content" }, archive: archive,
                                 reportCompletion: completion.complete)
        await job.start()?.value
        #expect(completion.values.withLock { $0 } == [false])
        #expect(!FileManager.default.fileExists(atPath: missingParentFile.path))
    }

    @Test
    func cancellationWithoutExpirationStillAwaitsShieldedCompletion() async throws {
        let fixture = try BackupTestFile()
        defer { fixture.remove() }
        let exportStarted = AsyncStream<Void>.makeStream()
        let exportGate = AsyncStream<Void>.makeStream()
        let completion = BackupCompletionLog()
        let job = CloudBackupJob(export: {
            exportStarted.continuation.yield(())
            var iterator = exportGate.stream.makeAsyncIterator()
            await iterator.next()
            // Simulate a legacy exporter returning content after cancellation.
            return "late content"
        }, archive: fixture.archive, reportCompletion: completion.complete)
        let worker = try #require(job.start())
        var started = exportStarted.stream.makeAsyncIterator()
        await started.next()
        worker.cancel()
        await worker.value
        #expect(!FileManager.default.fileExists(atPath: fixture.file.path))
        #expect(completion.values.withLock { $0 } == [false])
        #expect(completion.cancellationWasMasked.withLock { $0 })
    }

    @Test
    func expirationBeforeStartCompletesOnceAndPreventsExport() async throws {
        let fixture = try BackupTestFile()
        defer { fixture.remove() }
        let exportCalls = Mutex(0)
        let completion = BackupCompletionLog()
        let job = CloudBackupJob(export: {
            exportCalls.withLock { $0 += 1 }
            return "content"
        }, archive: fixture.archive, reportCompletion: completion.complete)
        job.expire()
        job.expire()
        #expect(job.start() == nil)
        await completion.wait()
        #expect(exportCalls.withLock { $0 } == 0)
        #expect(completion.values.withLock { $0 } == [false])
    }

    @Test
    func concurrentExpirationCancelsExporterAndNeverWritesLateContent() async throws {
        let fixture = try BackupTestFile()
        defer { fixture.remove() }
        let exportStarted = AsyncStream<Void>.makeStream()
        let exportGate = AsyncStream<Void>.makeStream()
        let completion = BackupCompletionLog()
        let job = CloudBackupJob(export: {
            exportStarted.continuation.yield(())
            var iterator = exportGate.stream.makeAsyncIterator()
            await iterator.next()
            return "late content"
        }, archive: fixture.archive, reportCompletion: completion.complete)
        let worker = try #require(job.start())
        var started = exportStarted.stream.makeAsyncIterator()
        await started.next()
        await withTaskGroup(of: Void.self) { group in
            for _ in 0..<64 { group.addTask { job.expire() } }
        }
        await worker.value
        await completion.wait()
        #expect(worker.isCancelled)
        #expect(!FileManager.default.fileExists(atPath: fixture.file.path))
        #expect(completion.values.withLock { $0 } == [false])
    }

    @Test
    func expirationFinalizesBeforeNonCooperativeWriteReturns() async throws {
        let archive = HeldBackupArchive()
        let completion = BackupCompletionLog()
        let job = CloudBackupJob(export: { "content" }, archive: archive,
                                 reportCompletion: completion.complete)
        let worker = try #require(job.start())
        var started = archive.started.stream.makeAsyncIterator()
        await started.next()
        job.expire()
        await completion.wait()
        #expect(await !archive.didReturnFromWrite)
        #expect(completion.values.withLock { $0 } == [false])
        await archive.releaseWrite()
        await worker.value
        #expect(completion.values.withLock { $0 } == [false])
    }

    @Test
    func completionAndExpirationRaceReportOneTerminalResult() async throws {
        let fixture = try BackupTestFile()
        defer { fixture.remove() }
        let completion = BackupCompletionLog()
        let job = CloudBackupJob(export: { "content" }, archive: fixture.archive,
                                 reportCompletion: completion.complete)
        let worker = try #require(job.start())
        await withTaskGroup(of: Void.self) { group in
            group.addTask { await worker.value }
            for _ in 0..<32 { group.addTask { job.expire() } }
        }
        await completion.wait()
        #expect(completion.values.withLock { $0.count } == 1)
    }
}

struct ICloudBackupArchiveTests {
    @Test
    func localFileRoundTripPreservesUTF8() async throws {
        let fixture = try BackupTestFile()
        defer { fixture.remove() }
        let text = #"{"version":4,"note":"caffè ☕","amount":12345}"#
        try await fixture.archive.writeBackup(text: text)
        #expect(try await fixture.archive.readBackup() == text)
        #expect(try Data(contentsOf: fixture.file) == Data(text.utf8))
    }

    @Test
    func missingFileUsesExistingNotFoundError() async throws {
        let fixture = try BackupTestFile()
        defer { fixture.remove() }
        await #expect(throws: ICloudBackupStoreError.notFound) {
            try await fixture.archive.readBackup()
        }
    }

    @Test
    func invalidUTF8UsesExistingUnreadableError() async throws {
        let fixture = try BackupTestFile()
        defer { fixture.remove() }
        try Data([0xff, 0xfe]).write(to: fixture.file)
        await #expect(throws: ICloudBackupStoreError.unreadable) {
            try await fixture.archive.readBackup()
        }
    }

    @Test
    func cancelledDownloadDoesNotReturnStaleLocalContent() async throws {
        let fixture = try BackupTestFile()
        defer { fixture.remove() }
        try Data("stale content".utf8).write(to: fixture.file)
        let file = fixture.file
        let waiting = AsyncStream<Void>.makeStream()
        let gate = AsyncStream<Void>.makeStream()
        let requests = Mutex(0)
        let archive = ICloudBackupArchive(
            resolveBackupURL: { file },
            isUbiquitousItem: { _ in true },
            requestDownload: { _ in requests.withLock { $0 += 1 } },
            isDownloaded: { _ in false },
            waitForNextCheck: {
                waiting.continuation.yield(())
                var iterator = gate.stream.makeAsyncIterator()
                await iterator.next()
                try Task.checkCancellation()
            }
        )
        let read = Task { try await archive.readBackup() }
        var waitEvents = waiting.stream.makeAsyncIterator()
        await waitEvents.next()
        read.cancel()
        await #expect(throws: CancellationError.self) { try await read.value }
        #expect(requests.withLock { $0 } == 1)
    }

    @Test
    func downloadReadWaitsForCurrentFileAndThenReadsActualBytes() async throws {
        let fixture = try BackupTestFile()
        defer { fixture.remove() }
        try Data("stale content".utf8).write(to: fixture.file)
        let file = fixture.file
        let current = Mutex(false)
        let waiting = AsyncStream<Void>.makeStream()
        let gate = AsyncStream<Void>.makeStream()
        let archive = ICloudBackupArchive(
            resolveBackupURL: { file }, isUbiquitousItem: { _ in true }, requestDownload: { _ in },
            isDownloaded: { _ in current.withLock { $0 } },
            waitForNextCheck: {
                waiting.continuation.yield(())
                var iterator = gate.stream.makeAsyncIterator()
                await iterator.next()
                try Task.checkCancellation()
            }
        )
        let read = Task { try await archive.readBackup() }
        var waitEvents = waiting.stream.makeAsyncIterator()
        await waitEvents.next()
        try Data("downloaded content".utf8).write(to: file, options: .atomic)
        current.withLock { $0 = true }
        gate.continuation.yield(())
        #expect(try await read.value == "downloaded content")
    }

    @Test
    func downloadDeadlineDoesNotReadStaleLocalCopy() async throws {
        let fixture = try BackupTestFile()
        defer { fixture.remove() }
        try Data("stale content".utf8).write(to: fixture.file)
        let file = fixture.file
        let archive = ICloudBackupArchive(
            resolveBackupURL: { file }, isUbiquitousItem: { _ in true },
            requestDownload: { _ in }, isDownloaded: { _ in false }, downloadTimeout: .zero
        )
        await #expect(throws: ICloudBackupStoreError.unreadable) { try await archive.readBackup() }
    }

    @MainActor
    @Test
    func fileResolutionAndWritingStayOffMainThread() async throws {
        let fixture = try BackupTestFile()
        defer { fixture.remove() }
        let file = fixture.file
        let observedMainThread = Mutex<Bool?>(nil)
        let archive = ICloudBackupArchive(resolveBackupURL: {
            observedMainThread.withLock { $0 = Thread.isMainThread }
            return file
        })
        try await archive.writeBackup(text: "content")
        #expect(observedMainThread.withLock { $0 } == false)
        #expect(try String(contentsOf: file, encoding: .utf8) == "content")
    }
}

struct FoundationFileIOTests {
    @Test
    func cancellationPreventsQueuedOperationStarting() async throws {
        let io = FoundationFileIO()
        let firstStarted = AsyncStream<Void>.makeStream()
        let secondStarted = AsyncStream<Void>.makeStream()
        let release = DispatchSemaphore(value: 0)
        let operationCalls = Mutex(0)
        let first = Task {
            try await io.run {
                firstStarted.continuation.yield(())
                release.wait()
            }
        }
        var started = firstStarted.stream.makeAsyncIterator()
        await started.next()
        let second = Task {
            secondStarted.continuation.yield(())
            try await io.run { operationCalls.withLock { $0 += 1 } }
        }
        var queued = secondStarted.stream.makeAsyncIterator()
        await queued.next()
        second.cancel()
        release.signal()
        try await first.value
        await #expect(throws: CancellationError.self) { try await second.value }
        #expect(operationCalls.withLock { $0 } == 0)
    }

    @Test
    func inFlightSynchronousResultIsNotDeliveredAfterCancellation() async throws {
        let io = FoundationFileIO()
        let started = AsyncStream<Void>.makeStream()
        let release = DispatchSemaphore(value: 0)
        let operation = Task {
            try await io.run {
                started.continuation.yield(())
                release.wait()
                return "completed synchronous I/O"
            }
        }
        var events = started.stream.makeAsyncIterator()
        await events.next()
        operation.cancel()
        release.signal()
        await #expect(throws: CancellationError.self) { try await operation.value }
    }
}
