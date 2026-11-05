import Foundation
import Testing
@testable import Spesify

@MainActor
struct SharedScreenshotLifecycleTests {
    @Test
    func foregroundImportUsesTheExistingPipelineAndAcknowledgesOnlyItsImage() async throws {
        var reads: [URL] = []
        var removed: [URL] = []
        var dependencies = dependencies()
        dependencies.screenshotCandidates = { url in
            reads.append(url)
            return [.init(amountInput: "12.34", descriptionText: "Coffee", dateMillis: nil, categoryId: nil)]
        }
        dependencies.removeSharedScreenshot = { removed.append($0) }
        let model = ContentTransferViewModel(dependencies: dependencies)
        await model.importSharedScreenshotIfNeeded()
        #expect(reads == [file])
        #expect(removed == [file])
        #expect(model.screenshot?.prefills.first?.amountInput == "12.34")
        #expect(!model.isProcessingScreenshot)
    }

    @Test
    func closingDuringImportKeepsTheScreenshotForTheNextLaunch() async throws {
        let gate = SharedScreenshotTestGate()
        var removed: [URL] = []
        var dependencies = dependencies()
        dependencies.screenshotCandidates = { _ in
            await gate.wait()
            return [.init(amountInput: "12.34", descriptionText: "Late", dateMillis: nil, categoryId: nil)]
        }
        dependencies.removeSharedScreenshot = { removed.append($0) }
        let model = ContentTransferViewModel(dependencies: dependencies)
        let importing = Task { await model.importSharedScreenshotIfNeeded() }
        await gate.waitUntilEntered()
        await model.importSharedScreenshotIfNeeded()
        model.stop()
        gate.open()
        await importing.value
        #expect(removed.isEmpty)
        #expect(model.screenshot == nil)
        #expect(model.feedback == nil)
    }

    @Test
    func emptyInboxDoesNotStartOCRorShowAnError() async {
        var reads = 0
        var dependencies = dependencies()
        dependencies.nextSharedScreenshot = { nil }
        dependencies.screenshotCandidates = { _ in reads += 1; return [] }
        let model = ContentTransferViewModel(dependencies: dependencies)
        await model.importSharedScreenshotIfNeeded()
        #expect(reads == 0)
        #expect(model.screenshot == nil)
        #expect(model.feedback == nil)
        #expect(!model.isProcessingScreenshot)
    }

    @Test
    func unusableScreenshotReportsTheExistingErrorAndDoesNotBlockLaterShares() async {
        var removed: [URL] = []
        var dependencies = dependencies()
        dependencies.removeSharedScreenshot = { removed.append($0) }
        let model = ContentTransferViewModel(dependencies: dependencies)
        await model.importSharedScreenshotIfNeeded()
        #expect(model.feedback?.message == appLocalized("I could not find a usable expense in this screenshot."))
        #expect(removed == [file])
        #expect(model.screenshot == nil)
    }

    private var file: URL { URL(fileURLWithPath: "/shared/screenshot.png") }

    private func dependencies() -> ContentTransferDependencies {
        let file = file
        return ContentTransferDependencies(
            readCsv: { _ in "" },
            importCsv: { _ in .init(successMessage: nil, errorMessage: nil) },
            exportCsv: { _, _ in .init(fileName: nil, content: nil, errorMessage: nil) },
            isImageDocument: { _ in true },
            screenshotCandidates: { _ in [] },
            refreshWidget: {},
            nextSharedScreenshot: { file }
        )
    }
}

@MainActor
private final class SharedScreenshotTestGate {
    private var continuation: CheckedContinuation<Void, Never>?
    private var entryWaiter: CheckedContinuation<Void, Never>?
    func wait() async {
        await withCheckedContinuation { continuation in
            self.continuation = continuation
            entryWaiter?.resume()
            entryWaiter = nil
        }
    }
    func waitUntilEntered() async {
        if continuation == nil { await withCheckedContinuation { entryWaiter = $0 } }
    }
    func open() { continuation?.resume(); continuation = nil }
}
