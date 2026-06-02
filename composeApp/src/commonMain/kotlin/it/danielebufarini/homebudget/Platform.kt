package it.danielebufarini.homebudget

interface Platform {
    val name: String
    val isIos: Boolean
}

expect fun getPlatform(): Platform
