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

class IosAddTransactionIntentResult(
    val status: String,
    val transactionId: String?,
    val message: String?,
    val needsConfirmation: Boolean
) {
    val isCreated: Boolean get() = status == STATUS_CREATED

    companion object {
        const val STATUS_CREATED = "created"
        const val STATUS_NEEDS_CONFIRMATION = "needs_confirmation"
        const val STATUS_FAILED = "failed"
    }
}

class IosAddTransactionIntentController {
    private val addTransactionUseCase: AddTransactionUseCase by lazy {
        if (KoinPlatformTools.defaultContext().getOrNull() == null) {
            initKoin()
        }
        KoinPlatformTools.defaultContext().get().get<AddTransactionUseCase>()
    }

    suspend fun addTransaction(
        kind: String,
        amount: Long,
        categoryName: String?,
        description: String?,
        dateMillis: Long
    ): IosAddTransactionIntentResult {
        val transactionKind = TransactionKind.fromExternalValue(kind)
            ?: return IosAddTransactionIntentResult(
                status = IosAddTransactionIntentResult.STATUS_NEEDS_CONFIRMATION,
                transactionId = null,
                message = "Please choose expense or income.",
                needsConfirmation = true
            )

        val result = withContext(Dispatchers.Default) {
            addTransactionUseCase.execute(
                AddTransactionCommand(
                    kind = transactionKind,
                    amount = amount,
                    categoryName = categoryName,
                    description = description,
                    dateMillis = dateMillis.takeIf { it > 0L },
                    source = TransactionCreationSource.IosAppIntent
                )
            )
        }
        return result.toIosIntentResult()
    }
}

private fun AddTransactionResult.toIosIntentResult(): IosAddTransactionIntentResult {
    return when (this) {
        is AddTransactionResult.Created -> IosAddTransactionIntentResult(
            status = IosAddTransactionIntentResult.STATUS_CREATED,
            transactionId = transactionId,
            message = "Transaction added.",
            needsConfirmation = false
        )
        is AddTransactionResult.NeedsConfirmation -> IosAddTransactionIntentResult(
            status = IosAddTransactionIntentResult.STATUS_NEEDS_CONFIRMATION,
            transactionId = null,
            message = message,
            needsConfirmation = true
        )
        is AddTransactionResult.Failed -> IosAddTransactionIntentResult(
            status = IosAddTransactionIntentResult.STATUS_FAILED,
            transactionId = null,
            message = message,
            needsConfirmation = false
        )
    }
}
