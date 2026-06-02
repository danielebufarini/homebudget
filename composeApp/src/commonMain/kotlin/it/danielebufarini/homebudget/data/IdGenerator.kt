package it.danielebufarini.homebudget.data

expect object IdGenerator {
    fun newId(prefix: String): String
}
