package it.danielebufarini.spesify.data

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.ExperimentalKotlinTestApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private val backupDiagnosticJson = Json { prettyPrint = true }

class BudgetBackupIdValidationTest {
    @Test
    fun duplicateCategoryDiagnosticsRetainEveryIdInFirstSeenOrderForAllVersions() = runTest {
        (1..4).forEach { version ->
            assertRejected(
                backup(version, categoryIds = listOf("z", "a", "a", "z")),
                "Duplicate category ids: z, a."
            )
        }
    }

    @Test
    fun duplicateExpenseDiagnosticsRetainEveryIdInFirstSeenOrderForAllVersions() = runTest {
        (1..4).forEach { version ->
            assertRejected(
                backup(version, expenseIds = listOf("z", "a", "a", "z")),
                "Duplicate expense ids: z, a."
            )
        }
    }

    @Test
    fun duplicateIncomeDiagnosticsRetainEveryIdInFirstSeenOrderForAllVersions() = runTest {
        (1..4).forEach { version ->
            assertRejected(
                backup(version, incomeIds = listOf("z", "a", "a", "z")),
                "Duplicate income ids: z, a."
            )
        }
    }

    @Test
    fun caseSensitiveIdsAndSingletonsRemainValidForEverySupportedVersion() = runTest {
        (1..4).forEach { version ->
            val text = backup(version, categoryIds = listOf("Ab", "ab"), expenseIds = listOf("E"), incomeIds = listOf("I"))
            assertCounters(text, BudgetBackupCounters(2, 1, 1, version = version))
        }
    }

    @Test
    fun emptyCollectionsRemainValidForEverySupportedVersion() = runTest {
        (1..4).forEach { version ->
            val text = backup(version, categoryIds = emptyList())
            assertCounters(text, BudgetBackupCounters(0, 0, 0, version = version))
        }
    }

    @Test
    fun checksumFailureStillPrecedesDuplicateDiagnostics() = runTest {
        assertRejected(
            backup(categoryIds = listOf("z", "z"), checksum = "0".repeat(64)),
            "Backup integrity check failed."
        )
    }

    @Test
    fun uniquenessDoesNotBypassRowValidationAndDuplicateValidationStillRunsFirst() = runTest {
        assertRejected(backup(categoryIds = listOf("")), "Backup category id is blank.")
        assertRejected(backup(categoryIds = listOf("", "")), "Duplicate category ids: .")
    }

    private suspend fun assertRejected(text: String, expected: String) {
        val error = assertFailsWith<IllegalArgumentException> { parseBudgetBackup(text) }
        assertBackupMessage(expected, error.message, text)
    }

    @OptIn(ExperimentalKotlinTestApi::class)
    private fun assertBackupMessage(expected: String, actual: String?, text: String) {
        assertEquals(expected, actual) { "Unexpected rejection of backup:\n${prettyBackup(text)}" }
    }

    @OptIn(ExperimentalKotlinTestApi::class)
    private suspend fun assertCounters(text: String, expected: BudgetBackupCounters) {
        assertEquals(expected, parseBudgetBackup(text)) { "Unexpected counters for backup:\n${prettyBackup(text)}" }
    }

    private fun prettyBackup(text: String): String = backupDiagnosticJson
        .encodeToString(JsonObject.serializer(), Json.decodeFromString<JsonObject>(text))

    private fun backup(
        version: Int = 4,
        categoryIds: List<String> = listOf("food"),
        expenseIds: List<String> = emptyList(),
        incomeIds: List<String> = emptyList(),
        checksum: String? = null
    ): String = buildJsonObject {
        put("format", "spesify_backup")
        put("version", version)
        if (checksum != null) put("checksumSha256", checksum)
        put("categories", JsonArray(categoryIds.map { id ->
            buildJsonObject { put("id", id); put("name", "Food"); put("icon", "category") }
        }))
        put("expenses", JsonArray(expenseIds.map { id ->
            buildJsonObject {
                put("id", id); put("amount", "1234"); put("date", 1L)
                put("categoryId", categoryIds.first())
            }
        }))
        put("incomes", JsonArray(incomeIds.map { id ->
            buildJsonObject { put("id", id); put("amount", "5678"); put("date", 1L) }
        }))
    }.toString()
}
