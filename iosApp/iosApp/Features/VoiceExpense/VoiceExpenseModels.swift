import Foundation
import FoundationModels

// Shared Swift voice-expense domain models used by parsing, recording, and UI state.

struct VoiceExpenseCategory: Identifiable {
    let id: String
    let name: String
}

struct VoiceExpenseSnapshotData {
    let categories: [VoiceExpenseCategory]
    let recentExpenses: [VoiceExpenseCandidate]
}

struct VoiceExpenseCandidate: Identifiable {
    let id: String
    let amountInput: String
    let categoryId: String
    let categoryName: String
    let description: String?
    let date: Date
    let isShared: Bool
}

struct VoiceExpenseDraft {
    let intent: VoiceExpenseInterpretation.Intent
    let expenseId: String?
    let amountInput: String
    let categoryId: String
    let categoryName: String
    let description: String?
    let date: Date
    let isShared: Bool
    let summary: String

    var actionLabel: String {
        intent == .create ? appLocalized("Create new expense") : appLocalized("Update existing expense")
    }

    var actionButtonTitle: String {
        intent == .create ? appLocalized("Save Expense") : appLocalized("Update Expense")
    }

    var amountLabel: String? {
        appAmountLabel(amountInput)
    }

    var dateLabel: String? {
        voiceExpenseDisplayDateFormatter.string(from: date)
    }
}

@Generable(description: "A parsed expense action extracted from a spoken user command.")
struct VoiceExpenseInterpretation {
    @Generable
    enum Intent {
        case create
        case update
        case needClarification
        case ignore
    }

    @Guide(description: "Whether the user wants to create a new expense, update an existing expense, needs clarification, or did not provide a usable expense command.")
    var intent: Intent

    @Guide(description: "Existing expense id to update. Use nil for new expenses, clarification, or ignored commands.")
    var expenseId: String?

    @Guide(description: "Expense amount in euros with two decimal digits, for example 12.50. Use nil when clarification is needed, the command is ignored, or the amount is unchanged in an update.")
    var amount: String?

    @Guide(description: "Category id from the provided categories. Use nil when clarification is needed, the command is ignored, or the category is unchanged in an update.")
    var categoryId: String?

    @Guide(description: "Category name that exactly matches one of the provided categories whenever possible. Use nil when clarification is needed, the command is ignored, or the category is unchanged in an update.")
    var categoryName: String?

    @Guide(description: "Short expense description. Use nil when omitted, unchanged, or not applicable.")
    var description: String?

    @Guide(description: "Date in yyyy-MM-dd format. Resolve relative dates like yesterday, today, and tomorrow to a concrete date. For new expenses with no spoken date, use today's date. For updates with no spoken date, use nil.")
    var date: String?

    @Guide(description: "Whether the expense is shared. For create, use false when not mentioned. For update, use nil when not mentioned.")
    var isShared: Bool?

    @Guide(description: "Short user-facing summary of the parsed action, clarification, or ignored command.")
    var summary: String
}
