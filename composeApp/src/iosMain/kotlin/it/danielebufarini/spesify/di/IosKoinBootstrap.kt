package it.danielebufarini.spesify.di

import org.koin.core.Koin
import org.koin.mp.KoinPlatformTools
import platform.Foundation.NSRecursiveLock

private val bootstrapLock = NSRecursiveLock()

/**
 * UI, App Intents, and background jobs share one process-local Koin container.
 * Checking and starting must stay inside the same lock: cold entrypoints can race.
 */
internal fun iosKoin(): Koin {
    bootstrapLock.lock()
    try {
        val context = KoinPlatformTools.defaultContext()
        context.getOrNull()?.let { return it }
        initKoin()
        return context.get()
    } finally {
        bootstrapLock.unlock()
    }
}
