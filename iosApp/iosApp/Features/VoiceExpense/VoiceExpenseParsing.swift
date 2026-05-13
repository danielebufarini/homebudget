@preconcurrency import ComposeApp
import Foundation
import FoundationModels

// Foundation Models-backed interpretation of spoken expense commands on iOS.

func parseExpenseIntent(
    transcript: String,
    categories: [VoiceExpenseCategory],
    expenses: [VoiceExpenseCandidate]
) async throws -> VoiceExpenseInterpretation {
    let session = LanguageModelSession(
        model: SystemLanguageModel.default,
        instructions: """
        You convert a spoken budget command into one structured expense action.
        The input may be in Italian or English.
        Prefer create when the user is adding a new expense.
        Prefer update only when one listed expense is a clear match.
        If the command is ambiguous, return needClarification.
        Use only category ids and category names from the provided category list.
        For update, keep omitted fields as null so the app can preserve the existing value.
        Resolve relative dates like yesterday, today, and tomorrow into yyyy-MM-dd dates.
        For create, if no date is spoken, use today's date.
        Return short summaries.
        """
    )

    let response = try await session.respond(
        generating: VoiceExpenseInterpretation.self
    ) {
        """
        Today's date: \(voiceExpenseISODateFormatter.string(from: Date()))

        Transcript:
        \(transcript)

        Categories:
        \(voiceExpensePromptCategoriesText(categories))

        Existing expenses available for updates:
        \(voiceExpensePromptExpensesText(expenses))
        """
    }

    return response.content
}

private func voiceExpensePromptCategoriesText(_ categories: [VoiceExpenseCategory]) -> String {
    categories
        .lazy
        .map { "- id=\($0.id), name=\($0.name)" }
        .joined(separator: "\n")
}

private func voiceExpensePromptExpensesText(_ expenses: [VoiceExpenseCandidate]) -> String {
    expenses
        .lazy
        .map { expense in
            let dateText = voiceExpenseISODateFormatter.string(from: expense.date)
            let description = expense.description.ifEmptyNil
            let shared = expense.isShared ? "yes" : "no"
            return "- id=\(expense.id), amount=\(expense.amountInput), date=\(dateText), categoryId=\(expense.categoryId), categoryName=\(expense.categoryName), shared=\(shared), description=\(description)"
        }
        .joined(separator: "\n")
}
