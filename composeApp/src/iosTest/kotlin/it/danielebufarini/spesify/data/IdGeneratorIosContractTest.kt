package it.danielebufarini.spesify.data

import kotlin.test.Test
import kotlin.test.assertEquals

class IdGeneratorIosContractTest {
    @Test
    fun iosKeepsUppercaseUuidAndTheExactPrefix() {
        repeat(32) {
            val id = IdGenerator.newId("MiXeD_è")
            val suffix = id.removePrefix("MiXeD_è-")
            assertEquals("MiXeD_è-$suffix", id)
            assertEquals(suffix.uppercase(), suffix)
        }
    }
}
