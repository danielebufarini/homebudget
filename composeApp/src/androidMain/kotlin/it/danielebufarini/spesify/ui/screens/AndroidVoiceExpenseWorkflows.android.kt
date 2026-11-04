package it.danielebufarini.spesify.ui.screens

import it.danielebufarini.spesify.data.runCatchingCancellable

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.GenerativeModel
import it.danielebufarini.spesify.data.ExpenseRepository
import it.danielebufarini.spesify.data.TransactionEditorService
import it.danielebufarini.spesify.database.Category

// High-level Android voice-expense orchestration steps used by the dialog UI.

internal data class AndroidVoiceExpenseAvailabilityCheckResult(
    val status: Int,
    val errorMessage: String?
)

internal data class AndroidVoiceExpenseDraftBuildResult(
    val draft: AndroidVoiceExpenseDraft?,
    val statusMessage: String
)

internal data class AndroidVoiceExpenseDraftCommitResult(
    val success: Boolean,
    val errorMessage: String?
)

internal suspend fun checkAndroidVoiceExpenseAvailability(
    generativeModel: GenerativeModel,
    uiStrings: AndroidVoiceExpenseUiStrings
): AndroidVoiceExpenseAvailabilityCheckResult {
    return runCatchingCancellable {
        generativeModel.checkStatus()
    }.fold(
        onSuccess = { status ->
            AndroidVoiceExpenseAvailabilityCheckResult(
                status = status,
                errorMessage = null
            )
        },
        onFailure = { error ->
            AndroidVoiceExpenseAvailabilityCheckResult(
                status = FeatureStatus.UNAVAILABLE,
                errorMessage = error.message ?: uiStrings.voiceExpenseStatusUnableToCheck
            )
        }
    )
}

internal suspend fun buildAndroidVoiceExpenseDraft(
    transcript: String,
    repository: ExpenseRepository,
    resolveCategoryName: (Category) -> String,
    generativeModel: GenerativeModel,
    availability: Int?,
    uiStrings: AndroidVoiceExpenseUiStrings
): AndroidVoiceExpenseDraftBuildResult {
    return runCatchingCancellable {
        val snapshot = loadAndroidVoiceExpenseSnapshot(
            repository = repository,
            resolveCategoryName = resolveCategoryName
        )
        val interpretation = interpretAndroidVoiceExpense(
            transcript = transcript,
            snapshot = snapshot,
            generativeModel = generativeModel,
            availability = availability
        )
        val draft = resolveAndroidVoiceExpenseDraft(
            interpretation = interpretation,
            snapshot = snapshot,
            transcript = transcript
        )
        AndroidVoiceExpenseDraftBuildResult(
            draft = draft,
            statusMessage = when {
                draft == null -> interpretation.summary ?: uiStrings.voiceExpenseStatusUnusable
                draft.action == AndroidVoiceExpenseActionKind.Update -> uiStrings.voiceExpenseStatusReadyToUpdate
                else -> uiStrings.voiceExpenseStatusReadyToSave
            }
        )
    }.fold(
        onSuccess = { result ->
            result
        },
        onFailure = { error ->
            AndroidVoiceExpenseDraftBuildResult(
                draft = null,
                statusMessage = error.message ?: uiStrings.voiceExpenseStatusUnableToInterpret
            )
        }
    )
}

internal suspend fun commitAndroidVoiceExpenseDraft(
    draft: AndroidVoiceExpenseDraft,
    editorService: TransactionEditorService,
    repository: ExpenseRepository,
    uiStrings: AndroidVoiceExpenseUiStrings
): AndroidVoiceExpenseDraftCommitResult {
    return runCatchingCancellable {
        persistAndroidVoiceExpenseDraft(
            draft = draft,
            editorService = editorService,
            repository = repository,
            uiStrings = uiStrings
        )
    }.fold(
        onSuccess = {
            AndroidVoiceExpenseDraftCommitResult(
                success = true,
                errorMessage = null
            )
        },
        onFailure = { error ->
            AndroidVoiceExpenseDraftCommitResult(
                success = false,
                errorMessage = error.message ?: uiStrings.unableToSaveExpense
            )
        }
    )
}
