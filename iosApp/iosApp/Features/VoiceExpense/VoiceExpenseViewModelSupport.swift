import Foundation
import FoundationModels

// Small view-model helpers for user-facing messages and draft construction in the iOS voice flow.

func voiceExpenseCommitButtonTitle(for draft: VoiceExpenseDraft?) -> String {
    draft?.actionButtonTitle ?? appLocalized("Save")
}

func voiceExpenseCommitBusyLabel(for intent: VoiceExpenseInterpretation.Intent) -> String {
    switch intent {
    case .create:
        return appLocalized("Saving expense...")
    case .update:
        return appLocalized("Updating expense...")
    case .needClarification, .ignore:
        return appLocalized("Preparing expense...")
    }
}

func voiceExpenseCommitFailureMessage(_ message: String?) -> String {
    message ?? appLocalized("Unable to save expense.")
}

func voiceExpenseSnapshotLoadingMessage() -> String {
    appLocalized("Loading budget data...")
}

func voiceExpenseSnapshotLoadFailureMessage() -> String {
    appLocalized("Unable to load expenses and categories.")
}

func voiceExpenseRecordingStartMessage() -> String {
    appLocalized("Starting microphone...")
}

func voiceExpenseNoSpeechMessage() -> String {
    appLocalized("No speech was captured.")
}

func voiceExpenseSnapshotStillLoadingMessage() -> String {
    appLocalized("Budget data is still loading.")
}

func voiceExpenseUnderstandingMessage() -> String {
    appLocalized("Understanding expense...")
}

func voiceExpenseAvailabilityStatusMessage(
    snapshotLoaded: Bool,
    availability: SystemLanguageModel.Availability
) -> String? {
    snapshotLoaded && SystemLanguageModel.default.isAvailable ? nil : availabilityMessage(for: availability)
}

func buildVoiceExpenseDraft(
    from interpretation: VoiceExpenseInterpretation,
    transcript: String,
    categoriesById: [String: VoiceExpenseCategory],
    expensesById: [String: VoiceExpenseCandidate]
) -> VoiceExpenseDraft? {
    switch interpretation.intent {
    case .create:
        return buildCreateVoiceExpenseDraft(
            interpretation: interpretation,
            transcript: transcript,
            categoriesById: categoriesById
        )
    case .update:
        return buildUpdateVoiceExpenseDraft(
            interpretation: interpretation,
            transcript: transcript,
            categoriesById: categoriesById,
            expensesById: expensesById
        )
    case .needClarification, .ignore:
        return nil
    }
}

private func buildCreateVoiceExpenseDraft(
    interpretation: VoiceExpenseInterpretation,
    transcript: String,
    categoriesById: [String: VoiceExpenseCategory]
) -> VoiceExpenseDraft? {
    guard
        let amountInput = normalizeAmountInput(interpretation.amount),
        let category = resolveVoiceExpenseCategory(
            categoryId: interpretation.categoryId,
            categoryName: interpretation.categoryName,
            transcript: transcript,
            summary: interpretation.summary,
            categoriesById: categoriesById
        )
    else {
        return nil
    }

    let resolvedDate = resolveExpenseDate(
        isoValue: interpretation.date,
        transcript: transcript,
        summary: interpretation.summary,
        defaultDate: Calendar.current.startOfDay(for: Date())
    )

    return VoiceExpenseDraft(
        intent: .create,
        expenseId: nil,
        amountInput: amountInput,
        categoryId: category.id,
        categoryName: category.name,
        description: interpretation.description.trimmedNilIfBlank,
        date: resolvedDate,
        isShared: interpretation.isShared ?? false,
        summary: interpretation.summary
    )
}

private func buildUpdateVoiceExpenseDraft(
    interpretation: VoiceExpenseInterpretation,
    transcript: String,
    categoriesById: [String: VoiceExpenseCategory],
    expensesById: [String: VoiceExpenseCandidate]
) -> VoiceExpenseDraft? {
    guard
        let expenseId = interpretation.expenseId,
        let existingExpense = expensesById[expenseId]
    else {
        return nil
    }

    let category = resolveVoiceExpenseCategory(
        categoryId: interpretation.categoryId,
        categoryName: interpretation.categoryName,
        transcript: transcript,
        summary: interpretation.summary,
        categoriesById: categoriesById
    ) ?? categoriesById[existingExpense.categoryId]
    guard let category else {
        return nil
    }

    return VoiceExpenseDraft(
        intent: .update,
        expenseId: expenseId,
        amountInput: normalizeAmountInput(interpretation.amount) ?? existingExpense.amountInput,
        categoryId: category.id,
        categoryName: category.name,
        description: interpretation.description ?? existingExpense.description,
        date: resolveExpenseDate(
            isoValue: interpretation.date,
            transcript: transcript,
            summary: interpretation.summary,
            defaultDate: existingExpense.date
        ),
        isShared: interpretation.isShared ?? existingExpense.isShared,
        summary: interpretation.summary
    )
}
