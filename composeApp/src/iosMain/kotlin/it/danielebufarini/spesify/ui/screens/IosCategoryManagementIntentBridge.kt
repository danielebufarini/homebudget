package it.danielebufarini.spesify.ui.screens

import it.danielebufarini.spesify.data.CategoryAgentUseCase
import it.danielebufarini.spesify.data.CategoryCommandResult
import it.danielebufarini.spesify.data.CategoryListResult
import it.danielebufarini.spesify.data.TransactionKind
import it.danielebufarini.spesify.di.initKoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatformTools

class IosCategoryListIntentResult(
    val status: String,
    val message: String
) {
    val isSuccess: Boolean get() = status == STATUS_SUCCESS

    companion object {
        const val STATUS_SUCCESS = "success"
        const val STATUS_FAILED = "failed"
    }
}

class IosCategoryCommandIntentResult(
    val status: String,
    val categoryId: String?,
    val message: String?,
    val needsConfirmation: Boolean
) {
    val isSuccess: Boolean get() = status == STATUS_SUCCESS

    companion object {
        const val STATUS_SUCCESS = "success"
        const val STATUS_NEEDS_CONFIRMATION = "needs_confirmation"
        const val STATUS_FAILED = "failed"
    }
}

class IosCategoryManagementIntentController {
    private val categoryAgentUseCase: CategoryAgentUseCase by lazy {
        if (KoinPlatformTools.defaultContext().getOrNull() == null) {
            initKoin()
        }
        KoinPlatformTools.defaultContext().get().get<CategoryAgentUseCase>()
    }

    suspend fun listCategories(kind: String): IosCategoryListIntentResult {
        val transactionKind = TransactionKind.fromExternalValue(kind)
            ?: return IosCategoryListIntentResult(
                status = IosCategoryListIntentResult.STATUS_FAILED,
                message = "Please choose expense or income."
            )
        return withContext(Dispatchers.Default) {
            categoryAgentUseCase.listCategories(kind = transactionKind)
        }.toIosCategoryListIntentResult()
    }

    suspend fun addCategory(
        kind: String,
        name: String,
        iconKey: String?
    ): IosCategoryCommandIntentResult {
        val transactionKind = TransactionKind.fromExternalValue(kind)
            ?: return IosCategoryCommandIntentResult(
                status = IosCategoryCommandIntentResult.STATUS_NEEDS_CONFIRMATION,
                categoryId = null,
                message = "Please choose expense or income.",
                needsConfirmation = true
            )
        return withContext(Dispatchers.Default) {
            categoryAgentUseCase.addCategory(
                kind = transactionKind,
                name = name,
                iconKey = iconKey
            )
        }.toIosCategoryCommandIntentResult()
    }

    suspend fun deleteCategory(
        kind: String,
        categoryName: String,
        moveToCategoryName: String
    ): IosCategoryCommandIntentResult {
        val transactionKind = TransactionKind.fromExternalValue(kind)
            ?: return IosCategoryCommandIntentResult(
                status = IosCategoryCommandIntentResult.STATUS_NEEDS_CONFIRMATION,
                categoryId = null,
                message = "Please choose expense or income.",
                needsConfirmation = true
            )
        return withContext(Dispatchers.Default) {
            categoryAgentUseCase.deleteCategory(
                kind = transactionKind,
                categoryNameOrId = categoryName,
                moveToCategoryNameOrId = moveToCategoryName
            )
        }.toIosCategoryCommandIntentResult()
    }
}

private fun CategoryListResult.toIosCategoryListIntentResult(): IosCategoryListIntentResult {
    return IosCategoryListIntentResult(
        status = status,
        message = message
    )
}

private fun CategoryCommandResult.toIosCategoryCommandIntentResult(): IosCategoryCommandIntentResult {
    return when (this) {
        is CategoryCommandResult.Success -> IosCategoryCommandIntentResult(
            status = IosCategoryCommandIntentResult.STATUS_SUCCESS,
            categoryId = categoryId,
            message = message,
            needsConfirmation = false
        )
        is CategoryCommandResult.NeedsConfirmation -> IosCategoryCommandIntentResult(
            status = IosCategoryCommandIntentResult.STATUS_NEEDS_CONFIRMATION,
            categoryId = null,
            message = message,
            needsConfirmation = true
        )
        is CategoryCommandResult.Failed -> IosCategoryCommandIntentResult(
            status = IosCategoryCommandIntentResult.STATUS_FAILED,
            categoryId = null,
            message = message,
            needsConfirmation = false
        )
    }
}
