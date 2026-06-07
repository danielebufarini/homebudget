package it.danielebufarini.spesify.di

import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

internal actual val databaseQueryCoroutineContext: CoroutineContext = Dispatchers.Default
