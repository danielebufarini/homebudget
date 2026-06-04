@preconcurrency import ComposeApp
import Foundation

// Thin Swift mapping around the Kotlin iOS voice-expense async controller.

extension IosVoiceExpenseController {
    func loadSnapshotData() async -> VoiceExpenseSnapshotData? {
        guard let snapshot = try? await loadSnapshot() else {
            return nil
        }
        return buildVoiceExpenseSnapshotData(from: snapshot)
    }

    func persist(draft: VoiceExpenseDraft) async -> (success: Bool, message: String?) {
        do {
            switch draft.intent {
            case .create:
                let result = try await createExpense(
                    amountInput: draft.amountInput,
                    categoryId: draft.categoryId,
                    description: draft.description,
                    date: Int64(draft.date.timeIntervalSince1970 * 1000.0),
                    isShared: draft.isShared
                )
                return (result.isSuccess, result.message)
            case .update:
                guard let expenseId = draft.expenseId else {
                    return (false, appLocalized("Expense not found."))
                }

                let result = try await updateExpense(
                    expenseId: expenseId,
                    amountInput: draft.amountInput,
                    categoryId: draft.categoryId,
                    description: draft.description,
                    date: Int64(draft.date.timeIntervalSince1970 * 1000.0),
                    isShared: draft.isShared
                )
                return (result.isSuccess, result.message)
            case .needClarification:
                return (false, appLocalized("The spoken command still needs clarification."))
            case .ignore:
                return (false, appLocalized("No usable expense command was found."))
            }
        } catch {
            return (false, error.localizedDescription)
        }
    }
}
