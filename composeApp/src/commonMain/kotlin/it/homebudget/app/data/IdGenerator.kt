package it.homebudget.app.data

expect object IdGenerator {
    fun newId(prefix: String): String
}
