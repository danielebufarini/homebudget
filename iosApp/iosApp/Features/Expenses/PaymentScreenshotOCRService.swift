@preconcurrency import ComposeApp
@preconcurrency import ImageIO
@preconcurrency import UIKit
@preconcurrency import Vision
import Foundation
import FoundationModels

@MainActor
protocol PaymentScreenshotOCRServicing {
    func recognizeText(in image: UIImage) async throws -> String
}

enum PaymentScreenshotOCRError: LocalizedError {
    case imageCannotBeRead
    case noText
    case recognitionFailed

    var errorDescription: String? {
        switch self {
        case .imageCannotBeRead:
            return appLocalized("Unable to read the selected screenshot.")
        case .noText:
            return appLocalized("No readable text was found in the screenshot.")
        case .recognitionFailed:
            return appLocalized("Unable to read text from this screenshot.")
        }
    }
}

@MainActor
final class VisionPaymentScreenshotOCRService: PaymentScreenshotOCRServicing {
    func recognizeText(in image: UIImage) async throws -> String {
        guard let imageData = image.pngData() ?? image.jpegData(compressionQuality: 1.0) else {
            throw PaymentScreenshotOCRError.imageCannotBeRead
        }

        let orientationRawValue = CGImagePropertyOrientation(image.imageOrientation).rawValue

        return try await Task.detached(priority: .userInitiated) {
            do {
                guard
                    let imageSource = CGImageSourceCreateWithData(imageData as CFData, nil),
                    let cgImage = CGImageSourceCreateImageAtIndex(imageSource, 0, nil)
                else {
                    throw PaymentScreenshotOCRError.imageCannotBeRead
                }

                let request = VNRecognizeTextRequest()
                request.recognitionLevel = .accurate
                request.usesLanguageCorrection = true
                Self.applyPreferredRecognitionLanguages(to: request)

                let orientation = CGImagePropertyOrientation(rawValue: orientationRawValue) ?? .up
                let handler = VNImageRequestHandler(
                    cgImage: cgImage,
                    orientation: orientation,
                    options: [:]
                )
                try handler.perform([request])

                let lines = (request.results ?? [])
                    .compactMap { $0.topCandidates(1).first?.string.trimmingCharacters(in: .whitespacesAndNewlines) }
                    .filter { !$0.isEmpty }

                let text = lines.joined(separator: "\n").trimmingCharacters(in: .whitespacesAndNewlines)
                guard !text.isEmpty else {
                    throw PaymentScreenshotOCRError.noText
                }

                return text
            } catch let error as PaymentScreenshotOCRError {
                throw error
            } catch {
                throw PaymentScreenshotOCRError.recognitionFailed
            }
        }.value
    }

    nonisolated private static func applyPreferredRecognitionLanguages(to request: VNRecognizeTextRequest) {
        let preferredLanguages = ["it-IT", "en-US"]

        do {
            let supportedLanguages = try VNRecognizeTextRequest.supportedRecognitionLanguages(
                for: .accurate,
                revision: request.revision
            )
            let availableLanguages = preferredLanguages.filter { supportedLanguages.contains($0) }
            if !availableLanguages.isEmpty {
                request.recognitionLanguages = availableLanguages
            }
        } catch {
            request.recognitionLanguages = preferredLanguages
        }
    }
}

private extension CGImagePropertyOrientation {
    init(_ orientation: UIImage.Orientation) {
        switch orientation {
        case .up:
            self = .up
        case .upMirrored:
            self = .upMirrored
        case .down:
            self = .down
        case .downMirrored:
            self = .downMirrored
        case .left:
            self = .left
        case .leftMirrored:
            self = .leftMirrored
        case .right:
            self = .right
        case .rightMirrored:
            self = .rightMirrored
        @unknown default:
            self = .up
        }
    }
}


@Generable(description: "One strict expense transaction extracted from OCR text.")
struct PaymentScreenshotLlmTransaction {
    @Guide(description: "True only when this item is a user expense or card/POS payment.")
    var isExpense: Bool

    @Guide(description: "Positive integer amount in minor units/cents, for example 1234 for EUR 12.34. Use nil if no reliable monetary amount exists.")
    var amountMinor: Int?

    @Guide(description: "Use EUR when present. Use nil only if EUR is safely inferable from the OCR text.")
    var currency: String?

    @Guide(description: "Merchant, payee, or short payment description. Use nil if unavailable.")
    var merchant: String?

    @Guide(description: "Confidence between 0 and 1 for this single transaction.")
    var confidence: Double
}

@Generable(description: "A strict expense JSON payload containing all payment transactions found in OCR text.")
struct PaymentScreenshotLlmResponse {
    @Guide(description: "All distinct expense transactions found in the OCR text. Return an empty array when no reliable expense exists.")
    var transactions: [PaymentScreenshotLlmTransaction]
}


private struct PaymentScreenshotLlmCompletion: @unchecked Sendable {
    let complete: (String) -> Void
}

final class FoundationModelsPaymentScreenshotLlmProvider: IosLocalLlmExpenseTextProvider, @unchecked Sendable {
    private let interpreter = FoundationModelsPaymentScreenshotLlmInterpreter()

    func interpret(text: String, completion: @escaping (String) -> Void) {
        let safeCompletion = PaymentScreenshotLlmCompletion(complete: completion)
        Task {
            let json = (try? await interpreter.interpret(text: text)) ?? ""
            safeCompletion.complete(json)
        }
    }
}

final class FoundationModelsPaymentScreenshotLlmInterpreter: @unchecked Sendable {
    func interpret(text: String) async throws -> String {
        let trimmedText = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedText.isEmpty, SystemLanguageModel.default.isAvailable else {
            return ""
        }

        let session = LanguageModelSession(
            model: SystemLanguageModel.default,
            instructions: """
            You interpret OCR text from a payment screenshot for a personal finance app.
            Run only on this device. Do not call external services.
            Produce one structured payload matching the requested schema.

            Rules:
            - Extract every distinct card payment/POS purchase/online purchase visible in the OCR text.
            - Return an empty transactions array if the text is not an expense, is a refund, is an income, is an incoming transfer, or is ambiguous.
            - Never use lock-screen/status-bar numbers such as time, date, battery percentage, or card suffixes as amounts.
            - amountMinor must be a positive integer number of cents/minor units. Example: EUR 12.34 becomes 1234.
            - Only use amounts that appear as money in the OCR text, such as 12,34 EUR, EUR 12,34, €12,34, or 12,34 €.
            - currency must be EUR when present. Leave it nil only when EUR is safely inferable.
            - merchant should be the merchant/payee/short description for that transaction, not the whole OCR text.
            - confidence must be between 0 and 1.
            """
        )

        let response = try await session.respond(
            generating: PaymentScreenshotLlmResponse.self
        ) {
            """
            OCR text:
            \(trimmedText)
            """
        }

        return Self.strictJson(from: response.content)
    }

    private static func strictJson(from response: PaymentScreenshotLlmResponse) -> String {
        let transactions = response.transactions.map { transaction -> [String: Any] in
            var payload: [String: Any] = [
                "isExpense": transaction.isExpense,
                "confidence": transaction.confidence
            ]

            if let amountMinor = transaction.amountMinor {
                payload["amountMinor"] = amountMinor
            }
            if let currency = transaction.currency?.trimmingCharacters(in: .whitespacesAndNewlines), !currency.isEmpty {
                payload["currency"] = currency
            }
            if let merchant = transaction.merchant?.trimmingCharacters(in: .whitespacesAndNewlines), !merchant.isEmpty {
                payload["merchant"] = merchant
            }

            return payload
        }

        let payload: [String: Any] = ["transactions": transactions]

        guard
            JSONSerialization.isValidJSONObject(payload),
            let data = try? JSONSerialization.data(withJSONObject: payload, options: [.sortedKeys]),
            let json = String(data: data, encoding: .utf8)
        else {
            return ""
        }

        return json
    }
}
