package it.homebudget.app.di

import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

internal actual val databaseQueryCoroutineContext: CoroutineContext = Dispatchers.IO
