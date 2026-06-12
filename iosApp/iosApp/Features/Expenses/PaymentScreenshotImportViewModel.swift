@preconcurrency import ComposeApp
import Foundation
import PhotosUI
import SwiftUI
import Observation
import UIKit

@MainActor
@Observable
final class PaymentScreenshotImportViewModel {
    var statusMessage = appLocalized("Choose a payment screenshot from Photos.")
    var errorMessage: String?
    var isBusy = false
    var busyLabel = ""

    private let ocrService: PaymentScreenshotOCRServicing
    private let controller: IosPaymentScreenshotController

    init(
        ocrService: PaymentScreenshotOCRServicing = VisionPaymentScreenshotOCRService(),
        controller: IosPaymentScreenshotController = IosPaymentScreenshotController()
    ) {
        self.ocrService = ocrService
        self.controller = controller
    }

    func processSelectedItem(
        _ item: PhotosPickerItem?,
        onCandidates: @escaping ([NativeExpenseEditorPrefill]) -> Void
    ) {
        guard let item else {
            errorMessage = appLocalized("No screenshot was selected.")
            return
        }

        isBusy = true
        busyLabel = appLocalized("Reading screenshot...")
        statusMessage = appLocalized("Reading screenshot...")
        errorMessage = nil

        Task { [weak self] in
            guard let self else {
                return
            }

            do {
                guard
                    let data = try await item.loadTransferable(type: Data.self),
                    let image = UIImage(data: data)
                else {
                    throw PaymentScreenshotImportError.imageLoadFailed
                }

                busyLabel = appLocalized("Preparing expense...")
                statusMessage = appLocalized("Preparing expense...")

                let prefills = try await candidates(from: image)

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
                isBusy = false
                busyLabel = ""
                statusMessage = appLocalized("Choose a payment screenshot from Photos.")
                errorMessage = paymentScreenshotImportMessage(for: error)
            }
        }
    }

    func candidates(fromImageFileAt url: URL) async throws -> [NativeExpenseEditorPrefill] {
        let data = try await Task.detached(priority: .userInitiated) {
            let didAccessSecurityScope = url.startAccessingSecurityScopedResource()
            defer {
                if didAccessSecurityScope {
                    url.stopAccessingSecurityScopedResource()
                }
            }

            return try Data(contentsOf: url)
        }.value

        guard let image = UIImage(data: data) else {
            throw PaymentScreenshotImportError.imageLoadFailed
        }

        return try await candidates(from: image)
    }

    private func candidates(from image: UIImage) async throws -> [NativeExpenseEditorPrefill] {
        let recognizedText = try await ocrService.recognizeText(in: image)
        let candidateQueue = try await controller.interpretOcrTextQueue(rawText: recognizedText)
        var prefills: [NativeExpenseEditorPrefill] = []
        while let candidate = candidateQueue.nextCandidate() {
            prefills.append(candidate.nativePrefill)
        }

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
