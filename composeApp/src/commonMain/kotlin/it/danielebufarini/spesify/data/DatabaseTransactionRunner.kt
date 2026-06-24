package it.danielebufarini.spesify.data

import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import it.danielebufarini.spesify.database.SpesifyDatabase

class DatabaseTransactionRunner(
    private val database: SpesifyDatabase
) {
    suspend fun <T> runInTransaction(block: suspend () -> T): T {
        return database.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                block()
            }
        }
    }
}
