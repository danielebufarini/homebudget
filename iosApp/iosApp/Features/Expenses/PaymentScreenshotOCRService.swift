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

    @Guide(description: "Exact visible money amount text copied from the OCR, including currency when visible, for example 44,99 EUR. Use nil if no reliable monetary amount exists.")
    var amountText: String?

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
            do {
                let json = try await interpreter.interpret(text: text)
                safeCompletion.complete(json)
            } catch {
                safeCompletion.complete("")
            }
        }
    }
}

final class FoundationModelsPaymentScreenshotLlmInterpreter: @unchecked Sendable {
    func interpret(text: String) async throws -> String {
        let trimmedText = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedText.isEmpty else {
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
            - amountText must be copied exactly from visible OCR money text. Do not calculate or invent numeric cents.
            - Valid amountText examples from OCR are forms like 44,99 EUR, EUR 44,99, €44,99, or 44,99 €.
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

            if let amountMinor = Self.amountMinor(from: transaction.amountText) {
                payload["amountMinor"] = amountMinor
            }
            if let amountText = transaction.amountText?.trimmingCharacters(in: .whitespacesAndNewlines), !amountText.isEmpty {
                payload["amountText"] = amountText
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

    private static func amountMinor(from amountText: String?) -> Int? {
        guard let amountText else {
            return nil
        }

        var normalized = amountText
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "\u{00A0}", with: "")
            .replacingOccurrences(of: " ", with: "")

        for currencyToken in ["EUR", "EURO", "EUROS", "€"] {
            normalized = normalized.replacingOccurrences(
                of: currencyToken,
                with: "",
                options: [.caseInsensitive]
            )
        }

        guard !normalized.isEmpty,
              !normalized.hasPrefix("-"),
              !normalized.hasPrefix("+"),
              normalized.allSatisfy({ $0.isNumber || $0 == "," || $0 == "." }) else {
            return nil
        }

        let lastComma = normalized.lastIndex(of: ",")
        let lastDot = normalized.lastIndex(of: ".")
        let decimalSeparator: Character?
        switch (lastComma, lastDot) {
        case (nil, nil):
            decimalSeparator = nil
        case let (comma?, nil):
            decimalSeparator = normalized.distance(from: comma, to: normalized.endIndex) <= 3 ? "," : nil
        case let (nil, dot?):
            decimalSeparator = normalized.distance(from: dot, to: normalized.endIndex) <= 3 ? "." : nil
        case let (comma?, dot?):
            decimalSeparator = comma > dot ? "," : "."
        }

        let majorText: String
        let minorText: String
        if let decimalSeparator,
           let separatorIndex = normalized.lastIndex(of: decimalSeparator) {
            let rawMajor = String(normalized[..<separatorIndex])
            let rawMinor = String(normalized[normalized.index(after: separatorIndex)...])
            guard !rawMajor.isEmpty,
                  !rawMinor.isEmpty,
                  rawMinor.count <= 2,
                  rawMinor.allSatisfy(\.isNumber) else {
                return nil
            }
            majorText = rawMajor.filter(\.isNumber)
            minorText = rawMinor.padding(toLength: 2, withPad: "0", startingAt: 0)
        } else {
            majorText = normalized.filter(\.isNumber)
            minorText = "00"
        }

        guard !majorText.isEmpty,
              let major = Int(majorText),
              let minor = Int(minorText) else {
            return nil
        }

        let amountMinor = major * 100 + minor
        return amountMinor > 0 ? amountMinor : nil
    }
}
