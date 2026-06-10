package it.danielebufarini.spesify.data.notifications

import android.content.Intent

object ExpenseConfirmationNotificationContract {
    const val ACTION_CONFIRM = "it.danielebufarini.spesify.action.CONFIRM_NOTIFICATION_EXPENSE"
    const val ACTION_MODIFY = "it.danielebufarini.spesify.action.MODIFY_NOTIFICATION_EXPENSE"
    const val ACTION_IGNORE = "it.danielebufarini.spesify.action.IGNORE_NOTIFICATION_EXPENSE"
    const val ACTION_REVIEW = "it.danielebufarini.spesify.action.REVIEW_NOTIFICATION_EXPENSE"

    const val EXTRA_CONFIRMATION_ID = "it.danielebufarini.spesify.extra.CONFIRMATION_ID"
    const val EXTRA_NOTIFICATION_ID = "it.danielebufarini.spesify.extra.NOTIFICATION_ID"
    const val EXTRA_AMOUNT_MINOR = "it.danielebufarini.spesify.extra.AMOUNT_MINOR"
    const val EXTRA_MERCHANT = "it.danielebufarini.spesify.extra.MERCHANT"
    const val EXTRA_CATEGORY_ID = "it.danielebufarini.spesify.extra.CATEGORY_ID"
    const val EXTRA_DATE_MILLIS = "it.danielebufarini.spesify.extra.DATE_MILLIS"

    internal fun Intent.readActionData(): ExpenseNotificationActionData? {
        val confirmationId = getStringExtra(EXTRA_CONFIRMATION_ID)?.takeIf(String::isNotBlank)
            ?: return null
        val amountMinor = getLongExtra(EXTRA_AMOUNT_MINOR, -1L).takeIf { it > 0L }
            ?: return null
        val dateMillis = getLongExtra(EXTRA_DATE_MILLIS, 0L)
            .takeIf { it > 0L }
            ?: ExpenseNotificationActionHandler.currentLocalDateMillis()
        val notificationId = getIntExtra(EXTRA_NOTIFICATION_ID, confirmationId.hashCode())
        return ExpenseNotificationActionData(
            confirmationId = confirmationId,
            notificationId = notificationId,
            amountMinor = amountMinor,
            merchant = getStringExtra(EXTRA_MERCHANT)?.takeIf(String::isNotBlank),
            categoryId = getStringExtra(EXTRA_CATEGORY_ID)?.takeIf(String::isNotBlank),
            dateMillis = dateMillis
        )
    }

    internal fun Intent.putActionData(data: ExpenseNotificationActionData): Intent = apply {
        putExtra(EXTRA_CONFIRMATION_ID, data.confirmationId)
        putExtra(EXTRA_NOTIFICATION_ID, data.notificationId)
        putExtra(EXTRA_AMOUNT_MINOR, data.amountMinor)
        putExtra(EXTRA_DATE_MILLIS, data.dateMillis)
        data.merchant?.let { putExtra(EXTRA_MERCHANT, it) }
        data.categoryId?.let { putExtra(EXTRA_CATEGORY_ID, it) }
    }

    internal fun ExpenseNotificationCandidate.toActionData(dateMillis: Long): ExpenseNotificationActionData =
        ExpenseNotificationActionData(
            confirmationId = confirmationId,
            notificationId = notificationId,
            amountMinor = amountMinor,
            merchant = merchant,
            categoryId = categoryId,
            dateMillis = dateMillis
        )
}
