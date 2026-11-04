package it.danielebufarini.spesify.di

import it.danielebufarini.spesify.NoOpPlatformStartupRestore
import it.danielebufarini.spesify.PlatformStartupRestore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertSame

class IosKoinBootstrapTest {
    @BeforeTest
    fun resetContainer() {
        stopKoin()
    }

    @AfterTest
    fun stopContainer() {
        stopKoin()
    }

    @Test
    fun concurrentColdEntrypoints_shareOneContainerBeforeAnyUiIsCreated() = runTest {
        val start = CompletableDeferred<Unit>()
        val entrypoints = List(16) {
            async(Dispatchers.Default) {
                start.await()
                iosKoin()
            }
        }
        start.complete(Unit)
        val containers = entrypoints.awaitAll()

        containers.forEach { assertSame(containers.first(), it) }
        assertSame(containers.first(), iosKoin())
        assertSame(NoOpPlatformStartupRestore, iosKoin().get<PlatformStartupRestore>())
    }

    @Test
    fun existingContainerAndItsBindings_arePreserved() {
        val marker = BootstrapMarker()
        val application = startKoin {
            modules(module { single { marker } })
        }

        assertSame(application.koin, iosKoin())
        assertSame(marker, iosKoin().get<BootstrapMarker>())
        assertSame(application.koin, iosKoin())
    }
}

private class BootstrapMarker
