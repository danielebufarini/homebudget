package it.danielebufarini.homebudget.data

import platform.Foundation.NSUUID

actual object IdGenerator {
    actual fun newId(prefix: String): String = "$prefix-${NSUUID().UUIDString}"
}
