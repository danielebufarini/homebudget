@preconcurrency import ComposeApp
import Foundation
import Observation
import UniformTypeIdentifiers

struct ContentCsvImportResult: Sendable {
    let successMessage: String?
    let errorMessage: String?
}

struct ContentCsvExportResult {
    let fileName: String?
    let content: String?
    let errorMessage: String?
}

struct ContentTransferFeedback: Identifiable {
    let id = UUID()
    let message: String
    let style: AppGlassBannerStyle
}

struct ContentTransferExport: Identifiable {
    let id = UUID()
    let fileName: String
    let content: String
}

struct ContentTransferCandidates: Identifiable {
    let id = UUID()
    let prefills: [NativeExpenseEditorPrefill]
}

@MainActor
struct ContentTransferDependencies {
    var readCsv: @MainActor (URL) async throws -> String
    var importCsv: @MainActor (String) async throws -> ContentCsvImportResult
    var exportCsv: @MainActor (Int64, Int64) async throws -> ContentCsvExportResult
    var isImageDocument: @MainActor (URL) async throws -> Bool
    var screenshotCandidates: @MainActor (URL) async throws -> [NativeExpenseEditorPrefill]
    var refreshWidget: @MainActor () -> Void
    var writes: ApplicationWriteTasks = .shared

    static func live() -> Self {
        let importer = IosCsvImportController()
        let exporter = IosCsvExportController()
        let screenshotImporter = PaymentScreenshotImportViewModel()
        return Self(
            readCsv: { try await ContentTransferFiles.readCsv(from: $0) },
            importCsv: { text in
                let result = try await importer.importCsv(text: text)
                return ContentCsvImportResult(successMessage: result.successMessage, errorMessage: result.errorMessage)
            },
            exportCsv: { start, end in
                let result = try await exporter.exportCsv(startDateMillis: start, endDateMillis: end)
                return ContentCsvExportResult(fileName: result.fileName, content: result.content, errorMessage: result.errorMessage)
            },
            isImageDocument: { try await ContentTransferFiles.isImageDocument($0) },
            screenshotCandidates: { try await screenshotImporter.candidates(fromImageFileAt: $0) },
            refreshWidget: { SpesifyWidgetSummaryRefresher.shared.refresh() }
        )
    }
}

enum ContentTransferOperation: Hashable { case csvImport, csvExport, screenshot }

@MainActor
@Observable
final class ContentTransferViewModel {
    private(set) var feedback: ContentTransferFeedback?
    private(set) var exportedCsv: ContentTransferExport?
    private(set) var screenshot: ContentTransferCandidates?
    private(set) var isProcessingScreenshot = false
    private let dependencies: ContentTransferDependencies
    @ObservationIgnored private var tasks: [ContentTransferOperation: Task<Void, Never>] = [:]
    @ObservationIgnored private var revisions: [ContentTransferOperation: Int] = [:]
    @ObservationIgnored private var presentationRevision = 0
    @ObservationIgnored private var active = true

    init(dependencies: ContentTransferDependencies = .live()) { self.dependencies = dependencies }

    deinit { tasks.values.forEach { $0.cancel() } }

    func start() { active = true }

    func stop() {
        active = false
        cancel(.csvImport)
        cancel(.csvExport)
        cancel(.screenshot)
        presentationRevision += 1
        isProcessingScreenshot = false
    }

    func task(for operation: ContentTransferOperation) -> Task<Void, Never>? { tasks[operation] }

    func cancelCsvImport() { cancel(.csvImport) }
    func cancelCsvExport() { cancel(.csvExport) }

    func importCsv(from url: URL) {
        launch(.csvImport) { [weak self, dependencies] revision in
            do {
                let text = try await dependencies.readCsv(url)
                try Task.checkCancellation()
                guard self?.isCurrent(.csvImport, revision) == true else { return }
                guard !text.isEmpty else {
                    self?.show(appLocalized("Unable to import the CSV file"), style: .error)
                    return
                }
                // Once handed to KMP, import can commit more than one transaction. Keep the
                // accepted write and its widget refresh alive independently of this UI awaiter.
                let importer = dependencies.importCsv
                let refreshWidget = dependencies.refreshWidget
                let accepted = dependencies.writes.perform {
                    let result = try await importer(text)
                    if result.successMessage != nil { refreshWidget() }
                    return result
                }
                let result = try await accepted.value
                try Task.checkCancellation()
                guard let self, isCurrent(.csvImport, revision) else { return }
                if let message = result.successMessage {
                    show(message, style: .success)
                } else if let message = result.errorMessage { show(message, style: .error) }
            } catch {
                guard !Task.isCancelled, !(error is CancellationError),
                      let self, isCurrent(.csvImport, revision) else { return }
                show(error.localizedDescription, style: .error)
            }
        }
    }

    func exportCsv(startDate: Date, endDate: Date) {
        guard active else { return }
        let start = Calendar.current.startOfDay(for: startDate)
        let end = Calendar.current.startOfDay(for: endDate)
        guard start <= end else {
            cancel(.csvExport)
            show(appLocalized("Start date must be on or before end date"), style: .error)
            return
        }
        launch(.csvExport) { [weak self, dependencies] revision in
            do {
                let result = try await dependencies.exportCsv(
                    Int64(start.timeIntervalSince1970 * 1000), Int64(end.timeIntervalSince1970 * 1000)
                )
                try Task.checkCancellation()
                guard let self, isCurrent(.csvExport, revision) else { return }
                if let fileName = result.fileName, let content = result.content {
                    exportedCsv = ContentTransferExport(fileName: fileName, content: content)
                } else {
                    show(result.errorMessage ?? appLocalized("Unable to export the CSV file"), style: .error)
                }
            } catch {
                guard !Task.isCancelled, !(error is CancellationError),
                      let self, isCurrent(.csvExport, revision) else { return }
                show(appLocalized("Unable to export the CSV file"), style: .error)
            }
        }
    }

    func importScreenshot(from url: URL) {
        guard active else { return }
        guard !isProcessingScreenshot else {
            show(appLocalized("Another payment screenshot is already being imported."), style: .error)
            return
        }
        isProcessingScreenshot = true
        launch(.screenshot) { [weak self, dependencies] revision in
            do {
                let isImage = try await dependencies.isImageDocument(url)
                try Task.checkCancellation()
                guard self?.isCurrent(.screenshot, revision) == true else { return }
                guard isImage else {
                    self?.show(appLocalized("Unable to import this payment screenshot."), style: .error)
                    return
                }
                let prefills = try await dependencies.screenshotCandidates(url)
                try Task.checkCancellation()
                guard let self, isCurrent(.screenshot, revision) else { return }
                guard !prefills.isEmpty else {
                    show(appLocalized("I could not find a usable expense in this screenshot."), style: .error)
                    return
                }
                screenshot = ContentTransferCandidates(prefills: prefills)
            } catch {
                guard !Task.isCancelled, !(error is CancellationError),
                      let self, isCurrent(.screenshot, revision) else { return }
                show(paymentScreenshotImportMessage(for: error), style: .error)
            }
        }
    }

    func presentAfterMenuDismiss(_ action: @escaping @MainActor () -> Void) {
        guard active else { return }
        presentationRevision += 1
        let revision = presentationRevision
        DispatchQueue.main.async { [weak self] in
            guard let self, active, presentationRevision == revision else { return }
            action()
        }
    }

    private func cancel(_ operation: ContentTransferOperation) {
        revisions[operation, default: 0] += 1
        tasks.removeValue(forKey: operation)?.cancel()
    }

    private func isCurrent(_ operation: ContentTransferOperation, _ revision: Int) -> Bool {
        active && revisions[operation] == revision
    }

    private func launch(_ operation: ContentTransferOperation, work: @escaping @MainActor (Int) async -> Void) {
        guard active else { return }
        cancel(operation)
        let revision = revisions[operation, default: 0]
        tasks[operation] = Task { [weak self] in
            defer {
                if let self, revisions[operation] == revision {
                    tasks[operation] = nil
                    if operation == .screenshot { isProcessingScreenshot = false }
                }
            }
            guard !Task.isCancelled else { return }
            await work(revision)
        }
    }

    private func show(_ message: String, style: AppGlassBannerStyle) {
        feedback = ContentTransferFeedback(message: message, style: style)
    }
}

enum ContentTransferFiles {
    static let maxCsvImportBytes = 5 * 1024 * 1024

    static func readCsv(from url: URL) async throws -> String {
        try await FoundationFileIO.shared.run {
            let didAccess = url.startAccessingSecurityScopedResource()
            defer { if didAccess { url.stopAccessingSecurityScopedResource() } }
            if let size = try? url.resourceValues(forKeys: [.fileSizeKey]).fileSize, size > maxCsvImportBytes {
                throw CsvImportReadError.fileTooLarge
            }
            let data = try Data(contentsOf: url, options: [.mappedIfSafe])
            guard data.count <= maxCsvImportBytes else { throw CsvImportReadError.fileTooLarge }
            return String(decoding: data, as: UTF8.self)
        }
    }

    static func isImageDocument(_ url: URL) async throws -> Bool {
        do {
            let type = try await FoundationFileIO.shared.run { try url.resourceValues(forKeys: [.contentTypeKey]).contentType }
            if type?.conforms(to: .image) == true { return true }
        } catch is CancellationError { throw CancellationError() }
        catch { /* A document provider may omit its content type; retain the extension fallback. */ }
        return UTType(filenameExtension: url.pathExtension)?.conforms(to: .image) == true
    }
}

private enum CsvImportReadError: LocalizedError {
    case fileTooLarge
    var errorDescription: String? { appLocalized("Unable to import the CSV file") }
}
