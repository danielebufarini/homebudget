package it.danielebufarini.spesify.data.notifications

internal class RecentNotificationDeduplicator(
    private val maxEntries: Int = 80,
    private val ttlMillis: Long = 10 * 60 * 1000L
) {
    private val entries = LinkedHashMap<String, Long>()

    @Synchronized
    fun shouldProcess(key: String, nowMillis: Long): Boolean {
        prune(nowMillis)
        if (entries.containsKey(key)) return false
        entries[key] = nowMillis
        while (entries.size > maxEntries) {
            val oldestKey = entries.keys.firstOrNull() ?: break
            entries.remove(oldestKey)
        }
        return true
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }

    private fun prune(nowMillis: Long) {
        val iterator = entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (nowMillis - entry.value > ttlMillis) {
                iterator.remove()
            }
        }
    }
}
