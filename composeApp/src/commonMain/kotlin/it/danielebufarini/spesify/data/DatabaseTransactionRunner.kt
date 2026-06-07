package it.danielebufarini.spesify.data

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
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
