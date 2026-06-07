package it.danielebufarini.spesify

import it.danielebufarini.spesify.data.BudgetBackupCounters

data class StartupRestorePrompt(
    val preview: BudgetBackupCounters
)

sealed interface StartupRestoreState {
    data object Ready : StartupRestoreState
    data class Pending(val prompt: StartupRestorePrompt) : StartupRestoreState
}

interface PlatformStartupRestore {
    suspend fun prepare(): StartupRestoreState
    suspend fun restorePending()
    suspend fun skipPending()
}

object NoOpPlatformStartupRestore : PlatformStartupRestore {
    override suspend fun prepare(): StartupRestoreState = StartupRestoreState.Ready

    override suspend fun restorePending() = Unit

    override suspend fun skipPending() = Unit
}
