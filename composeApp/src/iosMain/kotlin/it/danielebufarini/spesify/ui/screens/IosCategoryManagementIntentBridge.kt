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

class IosCategoryManagementIntentController {
    private val categoryAgentUseCase: CategoryAgentUseCase by lazy {
        if (KoinPlatformTools.defaultContext().getOrNull() == null) {
            initKoin()
        }
        KoinPlatformTools.defaultContext().get().get<CategoryAgentUseCase>()
    }

    suspend fun listCategories(kind: TransactionKind): IosCategoryListIntentResult {
        return withContext(Dispatchers.Default) {
            categoryAgentUseCase.listCategories(kind = kind)
        }.toIosCategoryListIntentResult()
    }

    suspend fun addCategory(
        kind: TransactionKind,
        name: String,
        iconKey: String?
    ): CategoryCommandResult {
        return withContext(Dispatchers.Default) {
            categoryAgentUseCase.addCategory(
                kind = kind,
                name = name,
                iconKey = iconKey
            )
        }
    }

    suspend fun deleteCategory(
        kind: TransactionKind,
        categoryName: String,
        moveToCategoryName: String
    ): CategoryCommandResult {
        return withContext(Dispatchers.Default) {
            categoryAgentUseCase.deleteCategory(
                kind = kind,
                categoryNameOrId = categoryName,
                moveToCategoryNameOrId = moveToCategoryName
            )
        }
    }
}

private fun CategoryListResult.toIosCategoryListIntentResult(): IosCategoryListIntentResult {
    return IosCategoryListIntentResult(
        status = status,
        message = message
    )
}
