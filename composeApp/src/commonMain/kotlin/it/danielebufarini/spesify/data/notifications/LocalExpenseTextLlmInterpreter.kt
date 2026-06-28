package it.danielebufarini.spesify.data.notifications

/**
 * Platform-neutral contract for on-device expense text interpretation.
 *
 * Implementations must run locally on the device and return the raw JSON string
 * produced by the platform model. Shared Kotlin code owns all validation and
 * normalization before a candidate can reach the editor or notification flow.
 */
interface LocalExpenseTextLlmInterpreter {
    suspend fun interpret(text: String): String

    suspend fun interpret(
        text: String,
        moneyCandidates: List<MoneyCandidate>
    ): String = interpret(text)
}
