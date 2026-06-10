package it.danielebufarini.spesify.data.notifications

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecentNotificationDeduplicatorTest {
    @Test
    fun rejectsDuplicateWithinTtl() {
        val deduplicator = RecentNotificationDeduplicator(ttlMillis = 1_000L)

        assertTrue(deduplicator.shouldProcess("key", nowMillis = 1_000L))
        assertFalse(deduplicator.shouldProcess("key", nowMillis = 1_500L))
    }

    @Test
    fun allowsSameKeyAfterTtl() {
        val deduplicator = RecentNotificationDeduplicator(ttlMillis = 1_000L)

        assertTrue(deduplicator.shouldProcess("key", nowMillis = 1_000L))
        assertTrue(deduplicator.shouldProcess("key", nowMillis = 2_500L))
    }
}
