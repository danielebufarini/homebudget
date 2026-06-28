package it.danielebufarini.spesify.data.notifications

import android.app.Notification
import android.os.Bundle
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class NotificationTextExtractorTest {
    @Test
    fun extractsTitleTextAndBigTextWithoutDuplicates() {
        val notification = Notification().apply {
            extras = Bundle().apply {
                putCharSequence(Notification.EXTRA_TITLE, "Pagamento")
                putCharSequence(Notification.EXTRA_TEXT, "Hai speso 12,50€ da Esselunga")
                putCharSequence(Notification.EXTRA_BIG_TEXT, "Hai speso 12,50€ da Esselunga")
            }
        }

        assertEquals(
            "Pagamento\nHai speso 12,50€ da Esselunga",
            NotificationTextExtractor.extract(notification)
        )
    }

    @Test
    fun extractsTextLines() {
        val notification = Notification().apply {
            extras = Bundle().apply {
                putCharSequenceArray(
                    Notification.EXTRA_TEXT_LINES,
                    arrayOf("Pagamento", "EUR 9,99 presso Bar Centrale")
                )
            }
        }

        assertEquals(
            "Pagamento\nEUR 9,99 presso Bar Centrale",
            NotificationTextExtractor.extract(notification)
        )
    }

    @Test
    fun returnsEmptyStringWhenExtrasAreMissing() {
        assertEquals("", NotificationTextExtractor.extract(Notification()))
    }
}
