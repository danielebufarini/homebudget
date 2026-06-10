package it.danielebufarini.spesify.data.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ExpenseNotificationActionReceiver : BroadcastReceiver(), KoinComponent {
    private val actionHandler: ExpenseNotificationActionHandler by inject()
    private val notifier: ExpenseConfirmationNotifier by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                handleIntent(context, intent)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleIntent(context: Context, intent: Intent) {
        val data = with(ExpenseConfirmationNotificationContract) { intent.readActionData() } ?: return
        when (intent.action) {
            ExpenseConfirmationNotificationContract.ACTION_CONFIRM -> handleConfirm(context, data)
            ExpenseConfirmationNotificationContract.ACTION_MODIFY -> handleModify(context, data)
            ExpenseConfirmationNotificationContract.ACTION_IGNORE -> handleIgnore(data)
        }
    }

    private suspend fun handleConfirm(context: Context, data: ExpenseNotificationActionData) {
        when (actionHandler.confirm(data)) {
            ExpenseNotificationConfirmResult.Saved,
            ExpenseNotificationConfirmResult.AlreadyHandled -> notifier.dismiss(data.notificationId)
            ExpenseNotificationConfirmResult.NeedsReview,
            ExpenseNotificationConfirmResult.Failed -> {
                notifier.dismiss(data.notificationId)
                context.openExpenseReview(data)
            }
        }
    }

    private suspend fun handleModify(context: Context, data: ExpenseNotificationActionData) {
        actionHandler.consumeWithoutSaving(data.confirmationId)
        notifier.dismiss(data.notificationId)
        context.openExpenseReview(data)
    }

    private suspend fun handleIgnore(data: ExpenseNotificationActionData) {
        actionHandler.consumeWithoutSaving(data.confirmationId)
        notifier.dismiss(data.notificationId)
    }
}

internal fun Context.openExpenseReview(data: ExpenseNotificationActionData) {
    val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return
    launchIntent.action = ExpenseConfirmationNotificationContract.ACTION_REVIEW
    launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    with(ExpenseConfirmationNotificationContract) {
        launchIntent.putActionData(data)
    }
    runCatching { startActivity(launchIntent) }
}
