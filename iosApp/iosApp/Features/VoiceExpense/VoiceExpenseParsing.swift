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
    let expensesText = try voiceExpensePromptExpensesText(expenses)

    let session = LanguageModelSession(
        model: SystemLanguageModel.default,
        instructions: buildVoiceExpensePromptInstructions(
            outputContract: buildIosVoiceExpenseOutputContract()
        )
    )

    let response = try await session.respond(
        generating: VoiceExpenseInterpretation.self,
        options: GenerationOptions(samplingMode: .greedy)
    ) {
        buildVoiceExpensePromptContext(
            currentDate: today,
            currentLanguageName: languageName,
            currentLanguageCode: languageCode,
            transcript: transcript,
            categoriesText: voiceExpensePromptCategoriesText(categories),
            expensesText: expensesText
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

func voiceExpensePromptExpensesText(_ expenses: [VoiceExpenseCandidate]) throws -> String {
    guard !expenses.isEmpty else { return "- none" }
    // State the field names once. Repeating seven labels for every recent expense can exhaust
    // the native model's context before generation. Keep every candidate, real ID and value;
    // JSON escaping also keeps description newlines/quotes inside their own field.
    struct Table: Encodable {
        let fields: [String]
        let rows: [[String]]
    }
    let table = Table(
        fields: ["id", "amount", "date", "categoryId", "categoryName", "shared", "description"],
        rows: expenses.map { expense in
            [
                expense.id,
                expense.amountInput,
                voiceExpenseISODateFormatter.string(from: expense.date),
                expense.categoryId,
                expense.categoryName,
                expense.isShared ? "yes" : "no",
                expense.description.ifEmptyNil ?? "none"
            ]
        }
    )
    let encoder = JSONEncoder()
    encoder.outputFormatting = [.sortedKeys, .withoutEscapingSlashes]
    return String(decoding: try encoder.encode(table), as: UTF8.self)
}
