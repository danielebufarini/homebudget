import FoundationModels

@MainActor
protocol VoiceExpenseRecording: AnyObject {
    func start(
        onTranscript: @escaping @MainActor @Sendable (String) -> Void,
        onFailure: @escaping @MainActor @Sendable (String) -> Void
    ) async throws
    @discardableResult func stop() -> String
}

extension VoiceExpenseRecording {
    func start(onTranscript: @escaping @MainActor @Sendable (String) -> Void) async throws {
        try await start(onTranscript: onTranscript, onFailure: { _ in })
    }
}

@MainActor
protocol VoiceExpenseControlling {
    func loadSnapshotData() async throws -> VoiceExpenseSnapshotData?
    func persist(draft: VoiceExpenseDraft) async throws -> (success: Bool, message: String?)
}

@MainActor
protocol VoiceExpenseRecognizing {
    var isAvailable: Bool { get }
    var unavailableMessage: String? { get }
    func interpret(
        transcript: String, categories: [VoiceExpenseCategory], expenses: [VoiceExpenseCandidate]
    ) async throws -> VoiceExpenseInterpretation
}

struct SystemVoiceExpenseRecognizer: VoiceExpenseRecognizing {
    var isAvailable: Bool { SystemLanguageModel.default.isAvailable }
    var unavailableMessage: String? { availabilityMessage(for: SystemLanguageModel.default.availability) }

    func interpret(
        transcript: String, categories: [VoiceExpenseCategory], expenses: [VoiceExpenseCandidate]
    ) async throws -> VoiceExpenseInterpretation {
        try await parseExpenseIntent(transcript: transcript, categories: categories, expenses: expenses)
    }
}
