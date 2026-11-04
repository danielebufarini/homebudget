package it.danielebufarini.spesify.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class IdGeneratorContractTest {
    @Test
    fun generatedIdsPreservePrefixesAndTheUuidVersionFourRepresentation() {
        listOf("expense", "income", "recurring", "MiXeD_è-", "").forEach { prefix ->
            val id = IdGenerator.newId(prefix)
            assertTrue(id.startsWith("$prefix-"))
            val uuid = id.removePrefix("$prefix-")
            assertEquals(36, uuid.length)
            assertEquals('4', uuid[14])
            assertTrue(uuid[19].lowercaseChar() in "89ab")
            assertEquals(uuid.lowercase(), Uuid.parse(uuid).toString())
        }
    }

    @Test
    fun generatedIdsAreDistinctAcrossCallsAndPrefixes() {
        val ids = List(1_000) { IdGenerator.newId(if (it % 2 == 0) "expense" else "income") }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(ids.size, ids.map { it.substringAfter('-').lowercase() }.toSet().size)
    }
}
