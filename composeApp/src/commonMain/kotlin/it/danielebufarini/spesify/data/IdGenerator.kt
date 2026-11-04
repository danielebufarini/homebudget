package it.danielebufarini.spesify.data

import kotlin.uuid.Uuid

expect object IdGenerator {
    fun newId(prefix: String): String
}

// Platform adapters retain their historical UUID casing; generation is shared.
internal fun newRandomUuidString(): String = Uuid.random().toString()
