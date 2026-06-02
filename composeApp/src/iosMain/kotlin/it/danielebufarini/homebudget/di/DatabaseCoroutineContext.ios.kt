package it.danielebufarini.homebudget.di

import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

internal actual val databaseQueryCoroutineContext: CoroutineContext = Dispatchers.Default
