package it.danielebufarini.homebudget.ui.screens

import com.google.mlkit.genai.common.FeatureStatus

// Small presentation formatters for Android voice expense dialog state.

internal fun buildAndroidVoiceExpenseSummary(
    draft: AndroidVoiceExpenseDraft,
    uiStrings: AndroidVoiceExpenseUiStrings
): String {
    return buildString {
        appendLine("${uiStrings.amount}: ${draft.amountInput}")
        appendLine("${uiStrings.category}: ${draft.categoryName}")
        appendLine("${uiStrings.date}: ${formatAndroidVoiceExpenseDate(draft.date)}")
        appendLine("${uiStrings.shared}: ${if (draft.isShared) uiStrings.yes else uiStrings.no}")
        draft.description?.takeIf { it.isNotBlank() }?.let { description ->
            append("${uiStrings.description}: $description")
        }
    }.trim()
}

internal fun buildAndroidVoiceAvailabilitySummary(
    status: Int?,
    uiStrings: AndroidVoiceExpenseUiStrings
): String {
    return when (status) {
        null -> uiStrings.voiceExpenseStatusCheckingAvailability
        FeatureStatus.AVAILABLE -> uiStrings.voiceExpenseAvailabilityAvailable
        FeatureStatus.DOWNLOADABLE -> uiStrings.voiceExpenseAvailabilityDownloadable
        FeatureStatus.DOWNLOADING -> uiStrings.voiceExpenseAvailabilityDownloading
        FeatureStatus.UNAVAILABLE -> uiStrings.voiceExpenseAvailabilityUnavailable
        else -> uiStrings.voiceExpenseAvailabilityUnknown
    }
}
