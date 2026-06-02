package it.danielebufarini.homebudget.data

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import it.danielebufarini.homebudget.database.HomeBudgetDatabase

class DatabaseTransactionRunner(
    private val database: HomeBudgetDatabase
) {
    suspend fun <T> runInTransaction(block: suspend () -> T): T {
        return database.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                block()
            }
        }
    }
}
