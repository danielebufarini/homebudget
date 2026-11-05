#if !targetEnvironment(simulator)
@preconcurrency import ComposeApp
import Foundation
import FoundationModels
import Testing
@testable import Spesify

/// Device-only provider acceptance after the real microphone/Speech boundary has been observed.
/// Supplies text directly, uses the production snapshot and recognizer, and performs no writes.
@MainActor
struct PhysicalVoiceTextAcceptanceTests {
    @Test
    func suppliedTextProducesARealModelDraftWithItsDescription() async throws {
        let controller = IosVoiceExpenseController()
        let snapshot = try #require(try await controller.loadSnapshotData())
        let recognizer = SystemVoiceExpenseRecognizer()
        try #require(recognizer.isAvailable, "Foundation Models must be available on the physical device")
        let text = "Ho speso dodici euro e trentaquattro oggi per cibo, descrizione collaudo zaffiro."
        let interpretation: VoiceExpenseInterpretation
        do {
            interpretation = try await recognizer.interpret(
                transcript: text,
                categories: snapshot.categories.sorted { $0.name < $1.name },
                expenses: Array(snapshot.recentExpenses.sorted { $0.date > $1.date }.prefix(80))
            )
        } catch {
            // Retain a technical discriminator without logging the financial prompt/context.
            let code = providerFailureCode(error)
            FileHandle.standardOutput.write(Data("PHYSICAL_TEXT_PROVIDER_ERROR \(code)\n".utf8))
            throw ProviderFailure(code: code)
        }
        #expect(interpretation.intent == .create)
        let categories = Dictionary(uniqueKeysWithValues: snapshot.categories.map { ($0.id, $0) })
        let expenses = Dictionary(uniqueKeysWithValues: snapshot.recentExpenses.map { ($0.id, $0) })
        let draft = try #require(buildVoiceExpenseDraft(
            from: interpretation, transcript: text, categoriesById: categories, expensesById: expenses
        ))
        #expect(draft.amountInput == "12.34")
        #expect(draft.categoryName == "Food")
        let description = try #require(draft.description)
        #expect(description.lowercased().contains("collaudo zaffiro"))
        #expect(Calendar.current.isDateInToday(draft.date))
        #expect(!draft.isShared)
        FileHandle.standardOutput.write(Data("PHYSICAL_TEXT_REAL_MODEL_DRAFT_VERIFIED\n".utf8))
    }

    private struct ProviderFailure: Error {
        let code: String
    }

    private func providerFailureCode(_ error: any Error) -> String {
        if #available(iOS 27.0, *), let error = error as? LanguageModelError {
            switch error {
            case let .contextSizeExceeded(context): return "contextSizeExceeded contextSize=\(context.contextSize) tokenCount=\(context.tokenCount)"
            case .guardrailViolation: return "guardrailViolation"
            case .unsupportedGenerationGuide: return "unsupportedGenerationGuide"
            case .unsupportedLanguageOrLocale: return "unsupportedLanguageOrLocale"
            case .rateLimited: return "rateLimited"
            case .refusal: return "refusal"
            case .unsupportedCapability: return "unsupportedCapability"
            case .unsupportedTranscriptContent: return "unsupportedTranscriptContent"
            case .timeout: return "timeout"
            @unknown default: return "LanguageModelError.unknown"
            }
        }
        if let error = error as? LanguageModelSession.GenerationError {
            switch error {
            case .exceededContextWindowSize: return "exceededContextWindowSize"
            case .assetsUnavailable: return "assetsUnavailable"
            case .guardrailViolation: return "guardrailViolation"
            case .unsupportedGuide: return "unsupportedGuide"
            case .unsupportedLanguageOrLocale: return "unsupportedLanguageOrLocale"
            case .decodingFailure: return "decodingFailure"
            case .rateLimited: return "rateLimited"
            case .concurrentRequests: return "concurrentRequests"
            case .refusal: return "refusal"
            @unknown default: return "GenerationError.unknown"
            }
        }
        let native = error as NSError
        return "\(String(reflecting: type(of: error))) domain=\(native.domain) code=\(native.code)"
    }
}
#endif
