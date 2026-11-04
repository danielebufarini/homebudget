import Foundation
import Testing
@testable import Spesify

struct ContentTransferFilesTests {
    @Test
    func fileReaderKeepsUtf8ReplacementAndTheFiveMiBBoundary() async throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let file = directory.appendingPathComponent("budget.csv")
        try Data([0x61, 0xFF, 0x62]).write(to: file)
        let decoded = try await ContentTransferFiles.readCsv(from: file)
        #expect(decoded == "a\u{FFFD}b")
        try Data(repeating: 0x61, count: 5 * 1024 * 1024).write(to: file)
        let boundary = try await ContentTransferFiles.readCsv(from: file)
        #expect(boundary.utf8.count == 5 * 1024 * 1024)
        try Data(repeating: 0x61, count: 5 * 1024 * 1024 + 1).write(to: file)
        do {
            _ = try await ContentTransferFiles.readCsv(from: file)
            Issue.record("CSV larger than five MiB was accepted")
        } catch {
            #expect(error.localizedDescription == appLocalized("Unable to import the CSV file"))
        }
    }

    @Test
    @MainActor
    func cancelledReadDoesNotBecomeAFileNotFoundError() async {
        let file = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        let task = Task { try await ContentTransferFiles.readCsv(from: file) }
        task.cancel()
        do {
            _ = try await task.value
            Issue.record("Cancelled CSV read returned a value")
        } catch {
            #expect(error is CancellationError)
        }
    }

    @Test
    func imageDocumentsRetainTheExtensionFallback() async throws {
        let missing = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        let image = try await ContentTransferFiles.isImageDocument(missing.appendingPathExtension("png"))
        let csv = try await ContentTransferFiles.isImageDocument(missing.appendingPathExtension("csv"))
        #expect(image)
        #expect(!csv)
    }
}
