package it.danielebufarini.spesify.data

expect object IdGenerator {
    fun newId(prefix: String): String
}
