package it.danielebufarini.homebudget.ui.screens

import kotlinx.datetime.LocalDate

// Shared Android voice-expense domain models used across parsing, speech, and persistence.

internal data class AndroidVoiceExpenseCategory(
    val id: String,
    val name: String
)

internal data class AndroidVoiceExpenseCandidate(
    val id: String,
    val amountInput: String,
    val categoryId: String,
    val categoryName: String,
    val description: String?,
    val date: Long,
    val isShared: Boolean
)

internal data class AndroidVoiceExpenseSnapshot(
    val categories: List<AndroidVoiceExpenseCategory>,
    val recentExpenses: List<AndroidVoiceExpenseCandidate>
)

internal enum class AndroidVoiceExpenseActionKind {
    Create,
    Update,
    NeedClarification,
    Ignore
}

internal data class AndroidVoiceExpenseInterpretation(
    val action: AndroidVoiceExpenseActionKind,
    val targetExpenseId: String?,
    val amountInput: String?,
    val categoryId: String?,
    val categoryName: String?,
    val description: String?,
    val date: LocalDate?,
    val isShared: Boolean?,
    val summary: String?
)

internal data class AndroidVoiceExpenseDraft(
    val action: AndroidVoiceExpenseActionKind,
    val expenseId: String?,
    val amountInput: String,
    val categoryId: String,
    val categoryName: String,
    val description: String?,
    val date: Long,
    val isShared: Boolean
)
