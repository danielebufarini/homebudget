import Foundation
import Testing
import UIKit
@testable import Spesify

@MainActor
struct PaymentScreenshotImportLifecycleTests {
    @Test
    func supersededScreenshotCannotPublishItsLateCandidates() async throws {
        let ocr = ScreenshotOCRStub()
        let model = PaymentScreenshotImportViewModel(
            ocrService: ocr, controller: ScreenshotControllerStub(), unavailableMessage: { nil }
        )
        let imageData = makeImageData()
        var candidates: [String] = []
        model.processImageData({ imageData }) { candidates += $0.map(\.descriptionText) }
        let first = try #require(model.processingTask)
        await ocr.waitForFirstRequest()
        model.processImageData({ imageData }) { candidates += $0.map(\.descriptionText) }
        await model.processingTask?.value
        ocr.finishFirstRequest()
        await first.value
        #expect(candidates == ["new"])
        #expect(!model.isBusy)
    }

    @Test
    func cancellationDuringOCRDoesNotInterpretOrPublishLateText() async throws {
        let ocr = ScreenshotOCRStub()
        let controller = ScreenshotControllerStub()
        let model = PaymentScreenshotImportViewModel(
            ocrService: ocr, controller: controller, unavailableMessage: { nil }
        )
        let imageData = makeImageData()
        var candidates: [NativeExpenseEditorPrefill] = []
        model.processImageData({ imageData }) { candidates += $0 }
        let pending = try #require(model.processingTask)
        await ocr.waitForFirstRequest()
        pending.cancel()
        ocr.finishFirstRequest()
        await pending.value
        #expect(controller.interpretedText.isEmpty)
        #expect(candidates.isEmpty)
        #expect(model.errorMessage == nil)
        #expect(!model.isBusy)
    }

    @Test
    func OCRCancellationIsNotDisplayedAsAnImportError() async throws {
        let ocr = ScreenshotOCRStub()
        ocr.error = CancellationError()
        let model = PaymentScreenshotImportViewModel(
            ocrService: ocr, controller: ScreenshotControllerStub(), unavailableMessage: { nil }
        )
        let imageData = makeImageData()
        model.processImageData({ imageData }) { _ in Issue.record("Cancelled import delivered candidates") }
        let pending = try #require(model.processingTask)
        await ocr.waitForFirstRequest()
        ocr.finishFirstRequest()
        await pending.value
        #expect(model.errorMessage == nil)
        #expect(!model.isBusy)
    }

    @Test
    func releasingTheOwnerCancelsItsOCRTaskWithoutWaitingForTheProvider() async throws {
        let ocr = ScreenshotOCRStub()
        var model: PaymentScreenshotImportViewModel? = PaymentScreenshotImportViewModel(
            ocrService: ocr, controller: ScreenshotControllerStub(), unavailableMessage: { nil }
        )
        weak let releasedModel = model
        let imageData = makeImageData()
        model?.processImageData({ imageData }) { _ in Issue.record("Released owner delivered candidates") }
        let pending = try #require(model?.processingTask)
        await ocr.waitForFirstRequest()
        model = nil
        #expect(releasedModel == nil)
        #expect(pending.isCancelled)
        ocr.finishFirstRequest()
        await pending.value
    }

    private func makeImageData() -> Data {
        UIGraphicsImageRenderer(size: CGSize(width: 1, height: 1)).pngData { _ in }
    }
}

@MainActor
private final class ScreenshotOCRStub: PaymentScreenshotOCRServicing {
    private var count = 0
    private var firstRequest: CheckedContinuation<Void, Never>?
    private var entryWaiters: [CheckedContinuation<Void, Never>] = []
    var error: (any Error)?

    func recognizeText(in image: UIImage) async throws -> String {
        count += 1
        if count == 1 {
            await withCheckedContinuation { continuation in
                firstRequest = continuation
                entryWaiters.forEach { $0.resume() }
                entryWaiters.removeAll()
            }
            if let error { throw error }
            return "old"
        }
        return "new"
    }

    func waitForFirstRequest() async {
        if firstRequest == nil { await withCheckedContinuation { entryWaiters.append($0) } }
    }

    func finishFirstRequest() {
        firstRequest?.resume()
        firstRequest = nil
    }
}

@MainActor
private final class ScreenshotControllerStub: PaymentScreenshotControlling {
    var interpretedText: [String] = []
    func expensePrefills(from recognizedText: String) async throws -> [NativeExpenseEditorPrefill] {
        interpretedText.append(recognizedText)
        return [.init(amountInput: "12.00", descriptionText: recognizedText, dateMillis: nil, categoryId: nil)]
    }
}
