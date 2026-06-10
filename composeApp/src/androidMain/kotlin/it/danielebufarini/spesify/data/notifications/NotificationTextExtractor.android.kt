package it.danielebufarini.spesify.data.notifications

import android.app.Notification

object NotificationTextExtractor {
    fun extract(notification: Notification): String {
        val extras = notification.extras ?: return ""
        val values = buildList {
            addCharSequence(extras.getCharSequence(Notification.EXTRA_TITLE))
            addCharSequence(extras.getCharSequence(Notification.EXTRA_TEXT))
            addCharSequence(extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
            addCharSequence(extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
            addCharSequence(extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT))
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                ?.forEach { line -> addCharSequence(line) }
        }
        return values
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(separator = "\n")
    }

    private fun MutableList<String>.addCharSequence(value: CharSequence?) {
        val text = value?.toString()?.trim().orEmpty()
        if (text.isNotBlank()) add(text)
    }
}
