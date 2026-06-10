package it.danielebufarini.spesify.data.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import it.danielebufarini.spesify.shared.R
import java.util.Locale

internal class ExpenseConfirmationNotifier(
    private val context: Context,
    private val permissionHelper: NotificationDetectionPermissionHelper
) {
    fun show(candidate: ExpenseNotificationCandidate) {
        if (!permissionHelper.canPostNotifications()) return

        ensureChannel()
        val actionData = with(ExpenseConfirmationNotificationContract) {
            candidate.toActionData(
                dateMillis = ExpenseNotificationActionHandler.currentLocalDateMillis()
            )
        }
        val contentText = if (candidate.merchant.isNullOrBlank()) {
            context.getString(
                R.string.notification_expense_confirmation_body_without_merchant,
                candidate.amountMinor.formatEuroAmount()
            )
        } else {
            context.getString(
                R.string.notification_expense_confirmation_body_with_merchant,
                candidate.amountMinor.formatEuroAmount(),
                candidate.merchant
            )
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_expense)
            .setContentTitle(context.getString(R.string.notification_expense_confirmation_title))
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(actionPendingIntent(ExpenseConfirmationNotificationContract.ACTION_MODIFY, actionData))
            .addAction(
                0,
                context.getString(R.string.notification_expense_action_confirm),
                actionPendingIntent(ExpenseConfirmationNotificationContract.ACTION_CONFIRM, actionData)
            )
            .addAction(
                0,
                context.getString(R.string.notification_expense_action_modify),
                actionPendingIntent(ExpenseConfirmationNotificationContract.ACTION_MODIFY, actionData)
            )
            .addAction(
                0,
                context.getString(R.string.notification_expense_action_ignore),
                actionPendingIntent(ExpenseConfirmationNotificationContract.ACTION_IGNORE, actionData)
            )
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(candidate.notificationId, notification)
        }
    }

    fun dismiss(notificationId: Int) {
        runCatching { NotificationManagerCompat.from(context).cancel(notificationId) }
    }

    private fun actionPendingIntent(action: String, data: ExpenseNotificationActionData): PendingIntent {
        val intent = Intent(context, ExpenseNotificationActionReceiver::class.java)
            .setAction(action)

        with(ExpenseConfirmationNotificationContract) {
            intent.putActionData(data)
        }
        return PendingIntent.getBroadcast(
            context,
            data.notificationId xor action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_expense_confirmation_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_expense_confirmation_channel_description)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun Long.formatEuroAmount(): String {
        val euros = this / 100
        val cents = (this % 100).toString().padStart(2, '0')
        return String.format(Locale.ITALY, "%d,%s", euros, cents)
    }

    private companion object {
        private const val CHANNEL_ID = "expense_confirmation"
    }
}
