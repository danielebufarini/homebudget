import Foundation
import Testing
@testable import Spesify

@MainActor
struct ContentTransferLifecycleTests {
    @Test
    func supersededCsvReadCannotStartAnotherImport() async throws {
        let gate = TransferTestGate()
        var imported: [String] = []
        var dependencies = testDependencies()
        dependencies.readCsv = { url in
            if url.lastPathComponent == "old.csv" { await gate.wait() }
            return url.lastPathComponent
        }
        dependencies.importCsv = { text in
            imported.append(text)
            return ContentCsvImportResult(successMessage: text, errorMessage: nil)
        }
        let model = ContentTransferViewModel(dependencies: dependencies)
        model.importCsv(from: URL(fileURLWithPath: "/old.csv"))
        let old = try #require(model.task(for: .csvImport))
        await gate.waitUntilEntered()
        model.importCsv(from: URL(fileURLWithPath: "/new.csv"))
        await model.task(for: .csvImport)?.value
        gate.open()
        await old.value
        #expect(imported == ["new.csv"])
        #expect(model.feedback?.message == "new.csv")
    }

    @Test
    func supersededExportCannotReplaceTheLatestFile() async throws {
        let gate = TransferTestGate()
        var calls = 0
        var dependencies = testDependencies()
        dependencies.exportCsv = { _, _ in
            calls += 1
            if calls == 1 {
                await gate.wait()
                return ContentCsvExportResult(fileName: "old.csv", content: "old", errorMessage: nil)
            }
            return ContentCsvExportResult(fileName: "new.csv", content: "new", errorMessage: nil)
        }
        let model = ContentTransferViewModel(dependencies: dependencies)
        model.exportCsv(startDate: date, endDate: date)
        let old = try #require(model.task(for: .csvExport))
        await gate.waitUntilEntered()
        model.exportCsv(startDate: date, endDate: date)
        await model.task(for: .csvExport)?.value
        gate.open()
        await old.value
        #expect(model.exportedCsv?.fileName == "new.csv")
        #expect(model.exportedCsv?.content == "new")
    }

    @Test
    func cancelledExportDoesNotOpenTheFileExporterOrShowAnError() async throws {
        let gate = TransferTestGate()
        var dependencies = testDependencies()
        dependencies.exportCsv = { _, _ in
            await gate.wait()
            return ContentCsvExportResult(fileName: "late.csv", content: "late", errorMessage: nil)
        }
        let model = ContentTransferViewModel(dependencies: dependencies)
        model.exportCsv(startDate: date, endDate: date)
        let pending = try #require(model.task(for: .csvExport))
        await gate.waitUntilEntered()
        model.cancelCsvExport()
        gate.open()
        await pending.value
        #expect(model.exportedCsv == nil)
        #expect(model.feedback == nil)
    }

    @Test
    func closingDuringDocumentOCRSuppressesLateNavigationAndFeedback() async throws {
        let gate = TransferTestGate()
        var dependencies = testDependencies()
        dependencies.screenshotCandidates = { _ in
            await gate.wait()
            return [.init(amountInput: "12.34", descriptionText: "late", dateMillis: nil, categoryId: nil)]
        }
        let model = ContentTransferViewModel(dependencies: dependencies)
        model.importScreenshot(from: URL(fileURLWithPath: "/receipt.png"))
        let pending = try #require(model.task(for: .screenshot))
        await gate.waitUntilEntered()
        model.stop()
        gate.open()
        await pending.value
        #expect(model.screenshot == nil)
        #expect(model.feedback == nil)
        #expect(!model.isProcessingScreenshot)
    }

    @Test
    func controllerCancellationIsNotAUserVisibleExportFailure() async {
        var dependencies = testDependencies()
        dependencies.exportCsv = { _, _ in throw CancellationError() }
        let model = ContentTransferViewModel(dependencies: dependencies)
        model.exportCsv(startDate: date, endDate: date)
        await model.task(for: .csvExport)?.value
        #expect(model.feedback == nil)
        #expect(model.exportedCsv == nil)
    }

    @Test
    func releasingTheOwnerCancelsItsReadAndPreventsAnImport() async throws {
        let gate = TransferTestGate()
        var imported: [String] = []
        var dependencies = testDependencies()
        dependencies.readCsv = { _ in await gate.wait(); return "late" }
        dependencies.importCsv = { text in
            imported.append(text)
            return ContentCsvImportResult(successMessage: text, errorMessage: nil)
        }
        var model: ContentTransferViewModel? = ContentTransferViewModel(dependencies: dependencies)
        weak let releasedModel = model
        model?.importCsv(from: URL(fileURLWithPath: "/late.csv"))
        let pending = try #require(model?.task(for: .csvImport))
        await gate.waitUntilEntered()
        model = nil
        #expect(releasedModel == nil)
        #expect(pending.isCancelled)
        gate.open()
        await pending.value
        #expect(imported.isEmpty)
    }

    @Test
    func successfulImportKeepsItsMessageAndRefreshesWidgetsOnce() async {
        var refreshes = 0
        var dependencies = testDependencies()
        dependencies.refreshWidget = { refreshes += 1 }
        let model = ContentTransferViewModel(dependencies: dependencies)
        model.importCsv(from: URL(fileURLWithPath: "/valid.csv"))
        await model.task(for: .csvImport)?.value
        #expect(model.feedback?.message == "Imported")
        #expect(refreshes == 1)
    }

    @Test
    func acceptedCsvImportFinishesAndRefreshesWidgetsAfterItsViewCloses() async throws {
        let gate = TransferTestGate()
        let writes = ApplicationWriteTasks()
        var committed: [String] = []
        var refreshes = 0
        var dependencies = testDependencies()
        dependencies.writes = writes
        dependencies.importCsv = { _ in
            // The real KMP importer can commit categories/expenses before reaching income writes.
            committed.append("categories")
            committed.append("expenses")
            await gate.wait()
            try Task.checkCancellation()
            committed.append("incomes")
            return ContentCsvImportResult(successMessage: "Imported", errorMessage: nil)
        }
        dependencies.refreshWidget = { refreshes += 1 }
        let model = ContentTransferViewModel(dependencies: dependencies)
        model.importCsv(from: URL(fileURLWithPath: "/accepted.csv"))
        let uiTask = try #require(model.task(for: .csvImport))
        await gate.waitUntilEntered()
        #expect(writes.pendingCount == 1)
        model.stop()
        gate.open()
        await uiTask.value
        #expect(committed == ["categories", "expenses", "incomes"])
        #expect(refreshes == 1)
        #expect(model.feedback == nil)
        #expect(writes.pendingCount == 0)
    }

    private var date: Date { Date(timeIntervalSince1970: 1_767_225_600) }

    private func testDependencies() -> ContentTransferDependencies {
        ContentTransferDependencies(
            readCsv: { _ in "csv" },
            importCsv: { _ in ContentCsvImportResult(successMessage: "Imported", errorMessage: nil) },
            exportCsv: { _, _ in ContentCsvExportResult(fileName: "budget.csv", content: "csv", errorMessage: nil) },
            isImageDocument: { _ in true }, screenshotCandidates: { _ in [] }, refreshWidget: {}
        )
    }
}

@MainActor
private final class TransferTestGate {
    private var entered = false
    private var opened = false
    private var waiters: [CheckedContinuation<Void, Never>] = []
    private var entryWaiters: [CheckedContinuation<Void, Never>] = []
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
