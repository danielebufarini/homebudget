@preconcurrency import ComposeApp
import Foundation
import PhotosUI
import SwiftUI
import Observation

@MainActor
protocol PaymentScreenshotControlling {
    func expensePrefills(from recognizedText: String) async throws -> [NativeExpenseEditorPrefill]
}

extension IosPaymentScreenshotController: PaymentScreenshotControlling {
    func expensePrefills(from recognizedText: String) async throws -> [NativeExpenseEditorPrefill] {
        let queue = try await interpretOcrTextQueue(rawText: recognizedText)
        var prefills: [NativeExpenseEditorPrefill] = []
        while let candidate = queue.nextCandidate() { prefills.append(candidate.nativePrefill) }
        return prefills
    }
}

@MainActor
@Observable
final class PaymentScreenshotImportViewModel {
    var statusMessage = appLocalized("Choose a payment screenshot from Photos.")
    var errorMessage: String?
    var isBusy = false
    var busyLabel = ""

    private let ocrService: PaymentScreenshotOCRServicing
    private let controller: any PaymentScreenshotControlling
    private let unavailableMessage: () -> String?
    @ObservationIgnored private(set) var processingTask: Task<Void, Never>?
    @ObservationIgnored private var generation = 0
    @ObservationIgnored private var disposed = false

    init(
        ocrService: PaymentScreenshotOCRServicing = VisionPaymentScreenshotOCRService(),
        controller: any PaymentScreenshotControlling = IosPaymentScreenshotController(),
        unavailableMessage: @escaping () -> String? = { PaymentScreenshotLlmAvailability.unavailableImportMessage() }
    ) {
        self.ocrService = ocrService
        self.controller = controller
        self.unavailableMessage = unavailableMessage
    }

    deinit { processingTask?.cancel() }

    func dispose() {
        disposed = true
        generation += 1
        processingTask?.cancel()
        isBusy = false
        busyLabel = ""
    }

    func processSelectedItem(
        _ item: PhotosPickerItem?,
        onCandidates: @escaping ([NativeExpenseEditorPrefill]) -> Void
    ) {
        guard !disposed else { return }
        guard let item else {
            generation += 1
            processingTask?.cancel()
            isBusy = false
            busyLabel = ""
            errorMessage = appLocalized("No screenshot was selected.")
            return
        }

        processImageData({ try await item.loadTransferable(type: Data.self) }, onCandidates: onCandidates)
    }

    func processImageData(
        _ loadImageData: @escaping @MainActor () async throws -> Data?,
        onCandidates: @escaping ([NativeExpenseEditorPrefill]) -> Void
    ) {
        guard !disposed else { return }
        generation += 1
        let requestGeneration = generation
        processingTask?.cancel()
        isBusy = true
        busyLabel = appLocalized("Reading screenshot...")
        statusMessage = appLocalized("Reading screenshot...")
        errorMessage = nil

        processingTask = Task { [weak self, ocrService, controller, unavailableMessage] in
            do {
                try Task.checkCancellation()
                guard
                    let data = try await loadImageData(),
                    let image = UIImage(data: data)
                else {
                    throw PaymentScreenshotImportError.imageLoadFailed
                }

                try Task.checkCancellation()
                guard self?.canPublish(requestGeneration) == true else { return }

                let recognizedText = try await ocrService.recognizeText(in: image)
                try Task.checkCancellation()
                guard self?.canPublish(requestGeneration) == true else { return }
                if let unavailableMessage = unavailableMessage() {
                    throw PaymentScreenshotLlmUnavailableError(message: unavailableMessage)
                }

                self?.busyLabel = appLocalized("Preparing expense...")
                self?.statusMessage = appLocalized("Preparing expense...")

                let prefills = try await controller.expensePrefills(from: recognizedText)
                try Task.checkCancellation()
                guard let self, canPublish(requestGeneration) else { return }

                guard !prefills.isEmpty else {
                    isBusy = false
                    busyLabel = ""
                    statusMessage = appLocalized("Choose a clearer payment screenshot and try again.")
                    errorMessage = appLocalized("I could not find a usable expense in this screenshot.")
                    return
                }

                isBusy = false
                busyLabel = ""
                statusMessage = prefills.count == 1 ? appLocalized("Expense ready to review.") : appLocalized("Expenses ready to review one at a time.")
                onCandidates(prefills)
            } catch {
                guard let self, canPublish(requestGeneration) else { return }
                isBusy = false
                busyLabel = ""
                statusMessage = appLocalized("Choose a payment screenshot from Photos.")
                errorMessage = (error is CancellationError || Task.isCancelled)
                    ? nil : paymentScreenshotImportMessage(for: error)
            }
        }
    }

    private func canPublish(_ requestGeneration: Int) -> Bool {
        !disposed && generation == requestGeneration
    }

    func candidates(fromImageFileAt url: URL) async throws -> [NativeExpenseEditorPrefill] {
        try Task.checkCancellation()
        let didAccessSecurityScope = url.startAccessingSecurityScopedResource()
        defer {
            if didAccessSecurityScope {
                url.stopAccessingSecurityScopedResource()
            }
        }

        let data = try await FoundationFileIO.shared.run { try Data(contentsOf: url, options: [.mappedIfSafe]) }
        guard let image = UIImage(data: data) else {
            throw PaymentScreenshotImportError.imageLoadFailed
        }

        return try await candidates(from: image)
    }

    private func candidates(from image: UIImage) async throws -> [NativeExpenseEditorPrefill] {
        try Task.checkCancellation()
        let recognizedText = try await ocrService.recognizeText(in: image)
        try Task.checkCancellation()
        if let unavailableMessage = unavailableMessage() {
            throw PaymentScreenshotLlmUnavailableError(message: unavailableMessage)
        }

        let prefills = try await controller.expensePrefills(from: recognizedText)
        try Task.checkCancellation()
        return prefills
    }
}

private enum PaymentScreenshotImportError: LocalizedError {
    case imageLoadFailed

    var errorDescription: String? {
        switch self {
        case .imageLoadFailed:
            return appLocalized("Unable to read the selected screenshot.")
        }
    }
}

private extension IosPaymentScreenshotExpenseCandidate {
    var nativePrefill: NativeExpenseEditorPrefill {
        NativeExpenseEditorPrefill(
            amountInput: amountInput,
            descriptionText: descriptionText,
            dateMillis: dateMillis > 0 ? dateMillis : nil,
            categoryId: categoryId
        )
    }
}

func paymentScreenshotImportMessage(for error: Error) -> String {
    if let localizedError = error as? LocalizedError,
       let description = localizedError.errorDescription,
       !description.isEmpty {
        return description
    }

    return appLocalized("Unable to import this payment screenshot.")
}
