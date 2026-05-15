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

    let session = LanguageModelSession(
        model: SystemLanguageModel.default,
        instructions: """
            You convert a spoken household budget command into one structured expense action.

            The input may be in Italian or English.

            Rules:
            - Return exactly one VoiceExpenseInterpretation.
            - Do not explain your reasoning.
            - Use intent=create when the user is adding a new expense.
            - Use intent=update only when the user clearly refers to exactly one of the listed existing expenses.
            - Use intent=needClarification when the transcript is probably an expense command, but important information is missing or ambiguous.
            - Use intent=ignore when the transcript is not a usable household expense command.
            - Prefer create when the user is adding a new expense.
            - Prefer update only when one listed existing expense is a clear match.
            - Never invent an expense id.
            - For update, copy the matching existing expense id exactly into expenseId.
            - For update, set only the fields explicitly mentioned by the user.
            - For update, leave omitted fields as nil so the app can preserve the existing value.
            - For create, amount is required. If the amount is missing, return needClarification.
            - For create, categoryId and categoryName are required. If the category is missing or cannot be matched, return needClarification.
            - amount must use a dot decimal separator and exactly two decimals, for example "12.50".
            - categoryId and categoryName must match one of the provided categories exactly.
            - Do not invent categories.
            - If the user gives only a category name, choose the matching category id from the provided category list.
            - date must be in yyyy-MM-dd format.
            - Resolve relative dates such as today, yesterday, tomorrow, last Monday, and similar expressions using the provided current date.
            - For create, if no date is spoken, use the provided current date.
            - For update, if no date is spoken, leave date as nil.
            - isShared must be true only when the user clearly says the expense is shared, split, divided, or paid together with someone else.
            - isShared must be false only when the user clearly says the expense is not shared.
            - If sharing is not mentioned, use false for create and nil for update.
            - description should be short and useful.
            - Omit unnecessary details from description.
            - summary should be a short user-facing summary.
            - For needClarification, summary must briefly ask for the missing or ambiguous information.
            - For ignore, summary must briefly say that no usable expense command was found.
            """
    )

    let response = try await session.respond(
        generating: VoiceExpenseInterpretation.self
    ) {
        """
        Current date:
        \(today)

        Transcript:
        \(transcript)

        Valid categories:
        \(voiceExpensePromptCategoriesText(categories))

        Existing expenses available for updates:
        \(voiceExpensePromptExpensesText(expenses))
        """
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