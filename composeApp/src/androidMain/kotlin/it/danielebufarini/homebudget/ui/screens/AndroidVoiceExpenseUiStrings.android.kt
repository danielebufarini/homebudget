package it.danielebufarini.homebudget.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.amount
import homebudget.composeapp.generated.resources.category
import homebudget.composeapp.generated.resources.close
import homebudget.composeapp.generated.resources.date
import homebudget.composeapp.generated.resources.description
import homebudget.composeapp.generated.resources.expense_id_missing
import homebudget.composeapp.generated.resources.expense_not_found
import homebudget.composeapp.generated.resources.new_expense
import homebudget.composeapp.generated.resources.no
import homebudget.composeapp.generated.resources.nothing_to_save
import homebudget.composeapp.generated.resources.refresh
import homebudget.composeapp.generated.resources.save_expense
import homebudget.composeapp.generated.resources.shared
import homebudget.composeapp.generated.resources.transcript
import homebudget.composeapp.generated.resources.unable_to_save_expense
import homebudget.composeapp.generated.resources.update
import homebudget.composeapp.generated.resources.update_expense
import homebudget.composeapp.generated.resources.voice_expense
import homebudget.composeapp.generated.resources.voice_expense_action_download_model
import homebudget.composeapp.generated.resources.voice_expense_action_listen_again
import homebudget.composeapp.generated.resources.voice_expense_action_start_listening
import homebudget.composeapp.generated.resources.voice_expense_action_stop_listening
import homebudget.composeapp.generated.resources.voice_expense_availability_available
import homebudget.composeapp.generated.resources.voice_expense_availability_downloadable
import homebudget.composeapp.generated.resources.voice_expense_availability_downloading
import homebudget.composeapp.generated.resources.voice_expense_availability_unavailable
import homebudget.composeapp.generated.resources.voice_expense_availability_unknown
import homebudget.composeapp.generated.resources.voice_expense_category_required
import homebudget.composeapp.generated.resources.voice_expense_empty_prompt
import homebudget.composeapp.generated.resources.voice_expense_invalid_amount
import homebudget.composeapp.generated.resources.voice_expense_model_downloading
import homebudget.composeapp.generated.resources.voice_expense_model_not_ready
import homebudget.composeapp.generated.resources.voice_expense_model_ready
import homebudget.composeapp.generated.resources.voice_expense_prompt
import homebudget.composeapp.generated.resources.voice_expense_status_audio_unavailable
import homebudget.composeapp.generated.resources.voice_expense_status_cancelled
import homebudget.composeapp.generated.resources.voice_expense_status_checking_availability
import homebudget.composeapp.generated.resources.voice_expense_status_downloading_available
import homebudget.composeapp.generated.resources.voice_expense_status_downloading_in_progress
import homebudget.composeapp.generated.resources.voice_expense_status_fallback_parser
import homebudget.composeapp.generated.resources.voice_expense_status_interpreting
import homebudget.composeapp.generated.resources.voice_expense_status_listening
import homebudget.composeapp.generated.resources.voice_expense_status_microphone_permission_required
import homebudget.composeapp.generated.resources.voice_expense_status_no_recognizer
import homebudget.composeapp.generated.resources.voice_expense_status_no_speech
import homebudget.composeapp.generated.resources.voice_expense_status_not_understood
import homebudget.composeapp.generated.resources.voice_expense_status_processing
import homebudget.composeapp.generated.resources.voice_expense_status_ready_to_save
import homebudget.composeapp.generated.resources.voice_expense_status_ready_to_update
import homebudget.composeapp.generated.resources.voice_expense_status_recognizer_busy
import homebudget.composeapp.generated.resources.voice_expense_status_service_unavailable
import homebudget.composeapp.generated.resources.voice_expense_status_unable_to_capture
import homebudget.composeapp.generated.resources.voice_expense_status_unable_to_check
import homebudget.composeapp.generated.resources.voice_expense_status_unable_to_download_model
import homebudget.composeapp.generated.resources.voice_expense_status_unable_to_interpret
import homebudget.composeapp.generated.resources.voice_expense_status_unable_to_start
import homebudget.composeapp.generated.resources.voice_expense_status_unable_to_stop
import homebudget.composeapp.generated.resources.voice_expense_status_unusable
import homebudget.composeapp.generated.resources.voice_expense_value_amount_positive
import homebudget.composeapp.generated.resources.yes
import org.jetbrains.compose.resources.stringResource

// Resource-backed UI strings consumed by the Android voice expense dialog and workflows.

internal data class AndroidVoiceExpenseUiStrings(
    val amount: String,
    val category: String,
    val close: String,
    val date: String,
    val description: String,
    val expenseIdMissing: String,
    val expenseNotFound: String,
    val newExpense: String,
    val no: String,
    val nothingToSave: String,
    val refresh: String,
    val saveExpense: String,
    val shared: String,
    val transcript: String,
    val unableToSaveExpense: String,
    val update: String,
    val updateExpense: String,
    val voiceExpense: String,
    val voiceExpenseActionDownloadModel: String,
    val voiceExpenseActionListenAgain: String,
    val voiceExpenseActionStartListening: String,
    val voiceExpenseActionStopListening: String,
    val voiceExpenseAvailabilityAvailable: String,
    val voiceExpenseAvailabilityDownloadable: String,
    val voiceExpenseAvailabilityDownloading: String,
    val voiceExpenseAvailabilityUnavailable: String,
    val voiceExpenseAvailabilityUnknown: String,
    val voiceExpenseCategoryRequired: String,
    val voiceExpenseEmptyPrompt: String,
    val voiceExpenseInvalidAmount: String,
    val voiceExpenseModelDownloading: String,
    val voiceExpenseModelNotReady: String,
    val voiceExpenseModelReady: String,
    val voiceExpensePrompt: String,
    val voiceExpenseStatusAudioUnavailable: String,
    val voiceExpenseStatusCancelled: String,
    val voiceExpenseStatusCheckingAvailability: String,
    val voiceExpenseStatusDownloadingAvailable: String,
    val voiceExpenseStatusDownloadingInProgress: String,
    val voiceExpenseStatusFallbackParser: String,
    val voiceExpenseStatusInterpreting: String,
    val voiceExpenseStatusListening: String,
    val voiceExpenseStatusMicrophonePermissionRequired: String,
    val voiceExpenseStatusNoRecognizer: String,
    val voiceExpenseStatusNoSpeech: String,
    val voiceExpenseStatusNotUnderstood: String,
    val voiceExpenseStatusProcessing: String,
    val voiceExpenseStatusReadyToSave: String,
    val voiceExpenseStatusReadyToUpdate: String,
    val voiceExpenseStatusRecognizerBusy: String,
    val voiceExpenseStatusServiceUnavailable: String,
    val voiceExpenseStatusUnableToCapture: String,
    val voiceExpenseStatusUnableToCheck: String,
    val voiceExpenseStatusUnableToDownloadModel: String,
    val voiceExpenseStatusUnableToInterpret: String,
    val voiceExpenseStatusUnableToStart: String,
    val voiceExpenseStatusUnableToStop: String,
    val voiceExpenseStatusUnusable: String,
    val voiceExpenseValueAmountPositive: String,
    val yes: String
)

@Composable
internal fun rememberAndroidVoiceExpenseUiStrings(): AndroidVoiceExpenseUiStrings {
    val amount = stringResource(Res.string.amount)
    val category = stringResource(Res.string.category)
    val close = stringResource(Res.string.close)
    val date = stringResource(Res.string.date)
    val description = stringResource(Res.string.description)
    val expenseIdMissing = stringResource(Res.string.expense_id_missing)
    val expenseNotFound = stringResource(Res.string.expense_not_found)
    val newExpense = stringResource(Res.string.new_expense)
    val no = stringResource(Res.string.no)
    val nothingToSave = stringResource(Res.string.nothing_to_save)
    val refresh = stringResource(Res.string.refresh)
    val saveExpense = stringResource(Res.string.save_expense)
    val shared = stringResource(Res.string.shared)
    val transcript = stringResource(Res.string.transcript)
    val unableToSaveExpense = stringResource(Res.string.unable_to_save_expense)
    val update = stringResource(Res.string.update)
    val updateExpense = stringResource(Res.string.update_expense)
    val voiceExpense = stringResource(Res.string.voice_expense)
    val voiceExpenseActionDownloadModel = stringResource(Res.string.voice_expense_action_download_model)
    val voiceExpenseActionListenAgain = stringResource(Res.string.voice_expense_action_listen_again)
    val voiceExpenseActionStartListening = stringResource(Res.string.voice_expense_action_start_listening)
    val voiceExpenseActionStopListening = stringResource(Res.string.voice_expense_action_stop_listening)
    val voiceExpenseAvailabilityAvailable = stringResource(Res.string.voice_expense_availability_available)
    val voiceExpenseAvailabilityDownloadable = stringResource(Res.string.voice_expense_availability_downloadable)
    val voiceExpenseAvailabilityDownloading = stringResource(Res.string.voice_expense_availability_downloading)
    val voiceExpenseAvailabilityUnavailable = stringResource(Res.string.voice_expense_availability_unavailable)
    val voiceExpenseAvailabilityUnknown = stringResource(Res.string.voice_expense_availability_unknown)
    val voiceExpenseCategoryRequired = stringResource(Res.string.voice_expense_category_required)
    val voiceExpenseEmptyPrompt = stringResource(Res.string.voice_expense_empty_prompt)
    val voiceExpenseInvalidAmount = stringResource(Res.string.voice_expense_invalid_amount)
    val voiceExpenseModelDownloading = stringResource(Res.string.voice_expense_model_downloading)
    val voiceExpenseModelNotReady = stringResource(Res.string.voice_expense_model_not_ready)
    val voiceExpenseModelReady = stringResource(Res.string.voice_expense_model_ready)
    val voiceExpensePrompt = stringResource(Res.string.voice_expense_prompt)
    val voiceExpenseStatusAudioUnavailable = stringResource(Res.string.voice_expense_status_audio_unavailable)
    val voiceExpenseStatusCancelled = stringResource(Res.string.voice_expense_status_cancelled)
    val voiceExpenseStatusCheckingAvailability = stringResource(Res.string.voice_expense_status_checking_availability)
    val voiceExpenseStatusDownloadingAvailable = stringResource(Res.string.voice_expense_status_downloading_available)
    val voiceExpenseStatusDownloadingInProgress = stringResource(Res.string.voice_expense_status_downloading_in_progress)
    val voiceExpenseStatusFallbackParser = stringResource(Res.string.voice_expense_status_fallback_parser)
    val voiceExpenseStatusInterpreting = stringResource(Res.string.voice_expense_status_interpreting)
    val voiceExpenseStatusListening = stringResource(Res.string.voice_expense_status_listening)
    val voiceExpenseStatusMicrophonePermissionRequired = stringResource(Res.string.voice_expense_status_microphone_permission_required)
    val voiceExpenseStatusNoRecognizer = stringResource(Res.string.voice_expense_status_no_recognizer)
    val voiceExpenseStatusNoSpeech = stringResource(Res.string.voice_expense_status_no_speech)
    val voiceExpenseStatusNotUnderstood = stringResource(Res.string.voice_expense_status_not_understood)
    val voiceExpenseStatusProcessing = stringResource(Res.string.voice_expense_status_processing)
    val voiceExpenseStatusReadyToSave = stringResource(Res.string.voice_expense_status_ready_to_save)
    val voiceExpenseStatusReadyToUpdate = stringResource(Res.string.voice_expense_status_ready_to_update)
    val voiceExpenseStatusRecognizerBusy = stringResource(Res.string.voice_expense_status_recognizer_busy)
    val voiceExpenseStatusServiceUnavailable = stringResource(Res.string.voice_expense_status_service_unavailable)
    val voiceExpenseStatusUnableToCapture = stringResource(Res.string.voice_expense_status_unable_to_capture)
    val voiceExpenseStatusUnableToCheck = stringResource(Res.string.voice_expense_status_unable_to_check)
    val voiceExpenseStatusUnableToDownloadModel = stringResource(Res.string.voice_expense_status_unable_to_download_model)
    val voiceExpenseStatusUnableToInterpret = stringResource(Res.string.voice_expense_status_unable_to_interpret)
    val voiceExpenseStatusUnableToStart = stringResource(Res.string.voice_expense_status_unable_to_start)
    val voiceExpenseStatusUnableToStop = stringResource(Res.string.voice_expense_status_unable_to_stop)
    val voiceExpenseStatusUnusable = stringResource(Res.string.voice_expense_status_unusable)
    val voiceExpenseValueAmountPositive = stringResource(Res.string.voice_expense_value_amount_positive)
    val yes = stringResource(Res.string.yes)

    return remember(
        amount,
        category,
        close,
        date,
        description,
        expenseIdMissing,
        expenseNotFound,
        newExpense,
        no,
        nothingToSave,
        refresh,
        saveExpense,
        shared,
        transcript,
        unableToSaveExpense,
        update,
        updateExpense,
        voiceExpense,
        voiceExpenseActionDownloadModel,
        voiceExpenseActionListenAgain,
        voiceExpenseActionStartListening,
        voiceExpenseActionStopListening,
        voiceExpenseAvailabilityAvailable,
        voiceExpenseAvailabilityDownloadable,
        voiceExpenseAvailabilityDownloading,
        voiceExpenseAvailabilityUnavailable,
        voiceExpenseAvailabilityUnknown,
        voiceExpenseCategoryRequired,
        voiceExpenseEmptyPrompt,
        voiceExpenseInvalidAmount,
        voiceExpenseModelDownloading,
        voiceExpenseModelNotReady,
        voiceExpenseModelReady,
        voiceExpensePrompt,
        voiceExpenseStatusAudioUnavailable,
        voiceExpenseStatusCancelled,
        voiceExpenseStatusCheckingAvailability,
        voiceExpenseStatusDownloadingAvailable,
        voiceExpenseStatusDownloadingInProgress,
        voiceExpenseStatusFallbackParser,
        voiceExpenseStatusInterpreting,
        voiceExpenseStatusListening,
        voiceExpenseStatusMicrophonePermissionRequired,
        voiceExpenseStatusNoRecognizer,
        voiceExpenseStatusNoSpeech,
        voiceExpenseStatusNotUnderstood,
        voiceExpenseStatusProcessing,
        voiceExpenseStatusReadyToSave,
        voiceExpenseStatusReadyToUpdate,
        voiceExpenseStatusRecognizerBusy,
        voiceExpenseStatusServiceUnavailable,
        voiceExpenseStatusUnableToCapture,
        voiceExpenseStatusUnableToCheck,
        voiceExpenseStatusUnableToDownloadModel,
        voiceExpenseStatusUnableToInterpret,
        voiceExpenseStatusUnableToStart,
        voiceExpenseStatusUnableToStop,
        voiceExpenseStatusUnusable,
        voiceExpenseValueAmountPositive,
        yes
    ) {
        AndroidVoiceExpenseUiStrings(
            amount = amount,
            category = category,
            close = close,
            date = date,
            description = description,
            expenseIdMissing = expenseIdMissing,
            expenseNotFound = expenseNotFound,
            newExpense = newExpense,
            no = no,
            nothingToSave = nothingToSave,
            refresh = refresh,
            saveExpense = saveExpense,
            shared = shared,
            transcript = transcript,
            unableToSaveExpense = unableToSaveExpense,
            update = update,
            updateExpense = updateExpense,
            voiceExpense = voiceExpense,
            voiceExpenseActionDownloadModel = voiceExpenseActionDownloadModel,
            voiceExpenseActionListenAgain = voiceExpenseActionListenAgain,
            voiceExpenseActionStartListening = voiceExpenseActionStartListening,
            voiceExpenseActionStopListening = voiceExpenseActionStopListening,
            voiceExpenseAvailabilityAvailable = voiceExpenseAvailabilityAvailable,
            voiceExpenseAvailabilityDownloadable = voiceExpenseAvailabilityDownloadable,
            voiceExpenseAvailabilityDownloading = voiceExpenseAvailabilityDownloading,
            voiceExpenseAvailabilityUnavailable = voiceExpenseAvailabilityUnavailable,
            voiceExpenseAvailabilityUnknown = voiceExpenseAvailabilityUnknown,
            voiceExpenseCategoryRequired = voiceExpenseCategoryRequired,
            voiceExpenseEmptyPrompt = voiceExpenseEmptyPrompt,
            voiceExpenseInvalidAmount = voiceExpenseInvalidAmount,
            voiceExpenseModelDownloading = voiceExpenseModelDownloading,
            voiceExpenseModelNotReady = voiceExpenseModelNotReady,
            voiceExpenseModelReady = voiceExpenseModelReady,
            voiceExpensePrompt = voiceExpensePrompt,
            voiceExpenseStatusAudioUnavailable = voiceExpenseStatusAudioUnavailable,
            voiceExpenseStatusCancelled = voiceExpenseStatusCancelled,
            voiceExpenseStatusCheckingAvailability = voiceExpenseStatusCheckingAvailability,
            voiceExpenseStatusDownloadingAvailable = voiceExpenseStatusDownloadingAvailable,
            voiceExpenseStatusDownloadingInProgress = voiceExpenseStatusDownloadingInProgress,
            voiceExpenseStatusFallbackParser = voiceExpenseStatusFallbackParser,
            voiceExpenseStatusInterpreting = voiceExpenseStatusInterpreting,
            voiceExpenseStatusListening = voiceExpenseStatusListening,
            voiceExpenseStatusMicrophonePermissionRequired = voiceExpenseStatusMicrophonePermissionRequired,
            voiceExpenseStatusNoRecognizer = voiceExpenseStatusNoRecognizer,
            voiceExpenseStatusNoSpeech = voiceExpenseStatusNoSpeech,
            voiceExpenseStatusNotUnderstood = voiceExpenseStatusNotUnderstood,
            voiceExpenseStatusProcessing = voiceExpenseStatusProcessing,
            voiceExpenseStatusReadyToSave = voiceExpenseStatusReadyToSave,
            voiceExpenseStatusReadyToUpdate = voiceExpenseStatusReadyToUpdate,
            voiceExpenseStatusRecognizerBusy = voiceExpenseStatusRecognizerBusy,
            voiceExpenseStatusServiceUnavailable = voiceExpenseStatusServiceUnavailable,
            voiceExpenseStatusUnableToCapture = voiceExpenseStatusUnableToCapture,
            voiceExpenseStatusUnableToCheck = voiceExpenseStatusUnableToCheck,
            voiceExpenseStatusUnableToDownloadModel = voiceExpenseStatusUnableToDownloadModel,
            voiceExpenseStatusUnableToInterpret = voiceExpenseStatusUnableToInterpret,
            voiceExpenseStatusUnableToStart = voiceExpenseStatusUnableToStart,
            voiceExpenseStatusUnableToStop = voiceExpenseStatusUnableToStop,
            voiceExpenseStatusUnusable = voiceExpenseStatusUnusable,
            voiceExpenseValueAmountPositive = voiceExpenseValueAmountPositive,
            yes = yes
        )
    }
}
