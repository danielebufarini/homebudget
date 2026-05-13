@preconcurrency import ComposeApp
import Foundation

// Async Swift wrapper around the Kotlin iOS voice-expense controller callbacks.

extension IosVoiceExpenseController {
    func loadSnapshotData() async -> VoiceExpenseSnapshotData? {
        await withCheckedContinuation { continuation in
            loadSnapshot { snapshot in
                continuation.resume(returning: snapshot.map(buildVoiceExpenseSnapshotData(from:)))
            }
        }
    }

    func persist(draft: VoiceExpenseDraft) async -> (success: Bool, message: String?) {
        await withCheckedContinuation { continuation in
            let completion: (KotlinBoolean?, String?) -> Void = { success, message in
                continuation.resume(returning: (success?.boolValue == true, message))
            }

            switch draft.intent {
            case .create:
                createExpense(
                    amountInput: draft.amountInput,
                    categoryId: draft.categoryId,
                    description: draft.description,
                    date: Int64(draft.date.timeIntervalSince1970 * 1000.0),
                    isShared: draft.isShared,
                    onComplete: completion
                )
            case .update:
                guard let expenseId = draft.expenseId else {
                    continuation.resume(returning: (false, appLocalized("Expense not found.")))
                    return
                }

                updateExpense(
                    expenseId: expenseId,
                    amountInput: draft.amountInput,
                    categoryId: draft.categoryId,
                    description: draft.description,
                    date: Int64(draft.date.timeIntervalSince1970 * 1000.0),
                    isShared: draft.isShared,
                    onComplete: completion
                )
            case .needClarification:
                continuation.resume(returning: (false, appLocalized("The spoken command still needs clarification.")))
            }
        }
    }
}
