package it.danielebufarini.spesify.data

import platform.Foundation.NSUUID

actual object IdGenerator {
    actual fun newId(prefix: String): String = "$prefix-${NSUUID().UUIDString}"
}
