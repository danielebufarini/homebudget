package it.danielebufarini.spesify.data

import androidx.room3.withWriteTransaction
import it.danielebufarini.spesify.database.SpesifyDatabase

class DatabaseTransactionRunner(
    private val database: SpesifyDatabase
) {
    suspend fun <T> runInTransaction(block: suspend () -> T): T {
        return database.withWriteTransaction {
            block()
        }
    }
}
