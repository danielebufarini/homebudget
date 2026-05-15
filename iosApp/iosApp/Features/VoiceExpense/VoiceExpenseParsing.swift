@preconcurrency import ComposeApp

import Foundation
import FoundationModels

// Foundation Models-backed interpretation of spoken expense commands on iOS.
func parseExpenseIntent(
    transcript: String,
    categories: [VoiceExpenseCategory],
    expenses: [VoiceExpenseCandidate]
) async throws -> VoiceExpenseInterpretation {
    let today = voiceExpenseISODateFormatter.string(from: Date())
    let locale = Locale.current
    let languageCode = locale.identifier
    let languageName = locale.localizedString(forIdentifier: languageCode) ?? languageCode

    let session = LanguageModelSession(
        model: SystemLanguageModel.default,
        instructions: VoiceExpensePromptKt.buildVoiceExpensePromptInstructions(
            outputContract: VoiceExpensePromptKt.buildIosVoiceExpenseOutputContract()
        )
    )

    let response = try await session.respond(
        generating: VoiceExpenseInterpretation.self
    ) {
        VoiceExpensePromptKt.buildVoiceExpensePromptContext(
            currentDate: today,
            currentLanguageName: languageName,
            currentLanguageCode: languageCode,
            transcript: transcript,
            categoriesText: voiceExpensePromptCategoriesText(categories),
            expensesText: voiceExpensePromptExpensesText(expenses)
        )
    }

    return response.content
}

private func voiceExpensePromptCategoriesText(_ categories: [VoiceExpenseCategory]) -> String {
    let text = categories
    .lazy
    .map { "- id=\($0.id), name=\($0.name)" }
    .joined(separator: "\n")

    return text.isEmpty ? "- none" : text
}

private func voiceExpensePromptExpensesText(_ expenses: [VoiceExpenseCandidate]) -> String {
    let text = expenses
    .lazy
    .map { expense in
        let dateText = voiceExpenseISODateFormatter.string(from: expense.date)
        let description = expense.description.ifEmptyNil ?? "none"
        let shared = expense.isShared ? "yes" : "no"

        return "- id=\(expense.id), amount=\(expense.amountInput), date=\(dateText), categoryId=\(expense.categoryId), categoryName=\(expense.categoryName), shared=\(shared), description=\(description)"
    }
    .joined(separator: "\n")

    return text.isEmpty ? "- none" : text
}
