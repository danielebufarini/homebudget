package it.danielebufarini.spesify.ui.screens

import it.danielebufarini.spesify.data.AddTransactionCommand
import it.danielebufarini.spesify.data.AddTransactionResult
import it.danielebufarini.spesify.data.AddTransactionUseCase
import it.danielebufarini.spesify.data.TransactionCreationSource
import it.danielebufarini.spesify.data.TransactionKind
import it.danielebufarini.spesify.di.initKoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatformTools

class IosAddTransactionIntentController {
    private val addTransactionUseCase: AddTransactionUseCase by lazy {
        if (KoinPlatformTools.defaultContext().getOrNull() == null) {
            initKoin()
        }
        KoinPlatformTools.defaultContext().get().get<AddTransactionUseCase>()
    }

    suspend fun addTransaction(
        kind: TransactionKind,
        amount: Long,
        categoryName: String?,
        description: String?,
        dateMillis: Long
    ): AddTransactionResult {
        return withContext(Dispatchers.Default) {
            addTransactionUseCase.execute(
                AddTransactionCommand(
                    kind = kind,
                    amount = amount,
                    categoryName = categoryName,
                    note = description,
                    dateMillis = dateMillis.takeIf { it > 0L },
                    source = TransactionCreationSource.IosAppIntent
                )
            )
        }
    }
}
