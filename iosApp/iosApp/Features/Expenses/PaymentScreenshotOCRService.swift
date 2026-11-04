@preconcurrency import ComposeApp
import ImageIO
import UIKit
import Vision
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

struct PaymentScreenshotLlmUnavailableError: LocalizedError {
    let message: String

    var errorDescription: String? {
        message
    }
}

enum PaymentScreenshotLlmAvailability {
    static func unavailableImportMessage(model: SystemLanguageModel = .default) -> String? {
        switch model.availability {
        case .available:
            break
        case let .unavailable(reason):
            switch reason {
            case .deviceNotEligible:
                return appLocalized("Foundation Models are unavailable on this device.")
            case .appleIntelligenceNotEnabled:
                return appLocalized("Apple Intelligence must be enabled to import payment screenshots.")
            case .modelNotReady:
                return appLocalized("The on-device model is still preparing. Try again in a moment.")
            @unknown default:
                return appLocalized("Foundation Models are currently unavailable.")
            }
        }

        guard model.supportsLocale() else {
            return appLocalized("Apple Intelligence does not support this app language on this device.")
        }

        return nil
    }
}

@MainActor
final class VisionPaymentScreenshotOCRService: PaymentScreenshotOCRServicing {
    func recognizeText(in image: UIImage) async throws -> String {
        try Task.checkCancellation()
        guard let imageData = image.pngData() ?? image.jpegData(compressionQuality: 1.0) else {
            throw PaymentScreenshotOCRError.imageCannotBeRead
        }

        return try await Self.recognizeText(data: imageData, orientation: CGImagePropertyOrientation(image.imageOrientation))
    }

    @concurrent
    private static func recognizeText(data: Data, orientation: CGImagePropertyOrientation) async throws -> String {
        do {
            try Task.checkCancellation()
            var request = RecognizeTextRequest()
            request.recognitionLevel = .accurate
            request.usesLanguageCorrection = true
            let preferredLanguages = [Locale.Language(identifier: "it-IT"), Locale.Language(identifier: "en-US")]
            let supportedLanguages = request.supportedRecognitionLanguages
            let availableLanguages = preferredLanguages.filter { supportedLanguages.contains($0) }
            if !availableLanguages.isEmpty { request.recognitionLanguages = availableLanguages }

            // Vision owns execution in the caller's task; cancellation no longer leaves a detached worker.
            let observations = try await request.perform(on: data, orientation: orientation)
            try Task.checkCancellation()
            let lines = observations.compactMap {
                $0.topCandidates(1).first?.string.trimmingCharacters(in: .whitespacesAndNewlines)
            }.filter { !$0.isEmpty }
            let text = lines.joined(separator: "\n").trimmingCharacters(in: .whitespacesAndNewlines)
            guard !text.isEmpty else { throw PaymentScreenshotOCRError.noText }
            return text
        } catch is CancellationError {
            throw CancellationError()
        } catch let error as PaymentScreenshotOCRError {
            throw error
        } catch {
            try Task.checkCancellation()
            throw PaymentScreenshotOCRError.recognitionFailed
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

    @Guide(description: "One selectedAmountMinor value copied exactly from the allowed candidate list. Use nil when none apply.")
    var selectedAmountMinor: Int?

    @Guide(description: "Currency for the selected candidate. Use EUR for EUR candidates, or nil when not an expense.")
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


struct PaymentScreenshotMoneyCandidate: Sendable {
    let amountMinor: Int64
    let currency: String?
    let originalText: String
}

final class FoundationModelsPaymentScreenshotLlmProvider: IosLocalLlmExpenseTextProvider, Sendable {
    typealias Interpretation = @Sendable (String, [PaymentScreenshotMoneyCandidate]) async throws -> String
    private let interpretation: Interpretation

    init(interpretation: @escaping Interpretation = { text, candidates in
        try await FoundationModelsPaymentScreenshotLlmInterpreter().interpret(text: text, moneyCandidates: candidates)
    }) {
        self.interpretation = interpretation
    }

    func interpret(
        text: String,
        moneyCandidates: [MoneyCandidate],
        completion: @escaping (String) -> Void
    ) -> any IosLocalLlmExpenseTextRequest {
        let request = PaymentScreenshotLlmRequest(completion: completion)
        let candidates = moneyCandidates.map {
            PaymentScreenshotMoneyCandidate(
                amountMinor: $0.amountMinor, currency: $0.currency, originalText: $0.originalText
            )
        }
        let task = Task { [interpretation] in
            do {
                try Task.checkCancellation()
                let json = try await interpretation(text, candidates)
                try Task.checkCancellation()
                request.finish(json)
            } catch {
                request.finish("")
            }
        }
        request.attach(task)
        return request
    }
}

struct FoundationModelsPaymentScreenshotLlmInterpreter: Sendable {
    func interpret(
        text: String,
        moneyCandidates: [PaymentScreenshotMoneyCandidate]
    ) async throws -> String {
        let trimmedText = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedText.isEmpty else {
            return ""
        }
        guard !moneyCandidates.isEmpty else {
            return ""
        }
        if let unavailableMessage = PaymentScreenshotLlmAvailability.unavailableImportMessage() {
            throw PaymentScreenshotLlmUnavailableError(message: unavailableMessage)
        }

        let session = LanguageModelSession(
            model: SystemLanguageModel.default,
            instructions: """
            You interpret OCR text from a payment screenshot for a personal finance app.
            Run only on this device. Do not call external services.
            Produce one structured payload matching the requested schema.

            Rules:
            - Extract every distinct card payment/POS purchase/online purchase visible in the OCR text.
            - If the screenshot contains multiple payment notifications, extract each reliable expense separately. Multiple notifications are not ambiguous by themselves.
            - Return an empty transactions array if the text is not an expense, is a refund, is an income, is an incoming transfer, or is ambiguous.
            - Never use lock-screen/status-bar numbers such as time, date, battery percentage, or card suffixes as amounts.
            - selectedAmountMinor must be one value from the allowed candidates list.
            - Do not parse, convert, calculate, or invent another amount.
            - currency must match the selected candidate.
            - merchant should be the merchant/payee/short description for that transaction, not the whole OCR text.
            - confidence must be between 0 and 1.
            """
        )

        let response = try await session.respond(
            generating: PaymentScreenshotLlmResponse.self
        ) {
            """
            Allowed amount candidates:
            \(Self.candidatesPrompt(moneyCandidates))

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

            if let selectedAmountMinor = transaction.selectedAmountMinor {
                payload["selectedAmountMinor"] = selectedAmountMinor
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

    private static func candidatesPrompt(_ moneyCandidates: [PaymentScreenshotMoneyCandidate]) -> String {
        moneyCandidates
            .map { candidate in
                let currency = candidate.currency ?? "null"
                let originalText = candidate.originalText
                    .replacingOccurrences(of: "\"", with: "\\\"")
                    .replacingOccurrences(of: "\n", with: " ")
                return "- selectedAmountMinor=\(candidate.amountMinor), currency=\(currency), text=\"\(originalText)\""
            }
            .joined(separator: "\n")
    }
}
