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
    }

    @Guide(description: "Whether the user wants to create a new expense, update an existing expense, or the command needs clarification.")
    var intent: Intent

    @Guide(description: "Existing expense id to update. Use null for new expenses or when clarification is needed.")
    var expenseId: String?

    @Guide(description: "Expense amount in euros with two decimal digits, for example 12.50. Use null only when clarification is needed or the amount is unchanged in an update.")
    var amount: String?

    @Guide(description: "Category id from the provided categories. Use null only when clarification is needed or the category is unchanged in an update.")
    var categoryId: String?

    @Guide(description: "Category name that exactly matches one of the provided categories whenever possible. Use null only when clarification is needed or the category is unchanged in an update.")
    var categoryName: String?

    @Guide(description: "Short expense description. Use null when omitted.")
    var description: String?

    @Guide(description: "Date in yyyy-MM-dd format. Resolve relative dates like yesterday, today, and tomorrow to a concrete date. For new expenses with no spoken date, use today's date.")
    var date: String?

    @Guide(description: "Whether the expense is shared. Use null when not mentioned for updates.")
    var isShared: Bool?

    @Guide(description: "Short user-facing summary of the parsed action or of the clarification needed.")
    var summary: String
}
