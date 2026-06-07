package it.danielebufarini.spesify.data

actual object IdGenerator {
    actual fun newId(prefix: String): String = "$prefix-${java.util.UUID.randomUUID()}"
}
