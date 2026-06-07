package it.danielebufarini.spesify

interface Platform {
    val name: String
    val isIos: Boolean
}

expect fun getPlatform(): Platform
