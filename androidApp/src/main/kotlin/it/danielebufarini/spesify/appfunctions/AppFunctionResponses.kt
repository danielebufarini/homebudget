package it.danielebufarini.spesify.appfunctions

import androidx.appfunctions.AppFunctionSerializable
import it.danielebufarini.spesify.data.AddTransactionResult
import it.danielebufarini.spesify.data.CategoryCommandResult
import it.danielebufarini.spesify.data.CategoryListResult
import it.danielebufarini.spesify.data.FinancialQueryResult
import it.danielebufarini.spesify.data.TransactionKind
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/**
 * Categories returned for agent-facing category discovery.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class CategoryListResponse(
    /** Result status: success or failed. */
    val status: String,
    /** Human-readable category list summary. */
    val message: String,
    /** Machine-readable JSON array string with id, name, kind, icon, color, isArchived, and isInUse fields. */
    val categoriesJson: String
) {
    companion object {
        fun failed(message: String): CategoryListResponse = CategoryListResponse(
            status = STATUS_FAILED,
            message = message,
            categoriesJson = "[]"
        )

        const val STATUS_SUCCESS = "success"
        const val STATUS_FAILED = "failed"
    }
}

/**
 * Result returned after adding or deleting a Spesify category.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class CategoryMutationResponse(
    /** Result status: success, needs_confirmation, or failed. */
    val status: String,
    /** Category ID when relevant. */
    val categoryId: String? = null,
    /** Human-readable message for the agent or assistant. */
    val message: String? = null,
    /** True when the agent should ask the user for more information before retrying. */
    val needsConfirmation: Boolean = false
) {
    companion object {
        fun needsConfirmation(message: String): CategoryMutationResponse = CategoryMutationResponse(
            status = STATUS_NEEDS_CONFIRMATION,
            message = message,
            needsConfirmation = true
        )

        const val STATUS_SUCCESS = "success"
        const val STATUS_NEEDS_CONFIRMATION = "needs_confirmation"
        const val STATUS_FAILED = "failed"
    }
}

/**
 * Result returned after trying to create a Spesify transaction.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class AddTransactionResponse(
    /** Result status: created, needs_confirmation, or failed. */
    val status: String,
    /** Created transaction ID when status is created. */
    val transactionId: String? = null,
    /** Human-readable message for the agent or assistant. */
    val message: String? = null,
    /** True when the agent should ask the user for more information before retrying. */
    val needsConfirmation: Boolean = false
) {
    companion object {
        fun needsConfirmation(message: String): AddTransactionResponse = AddTransactionResponse(
            status = STATUS_NEEDS_CONFIRMATION,
            message = message,
            needsConfirmation = true
        )

        const val STATUS_CREATED = "created"
        const val STATUS_NEEDS_CONFIRMATION = "needs_confirmation"
        const val STATUS_FAILED = "failed"
    }
}

/**
 * Result returned for financial total and balance queries.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class FinancialAmountResponse(
    /** Result status: success or failed. */
    val status: String,
    /** Amount in Spesify minor currency units. */
    val amountMinorUnits: Long,
    /** Localized display value for the user, such as "$55.56" or "€55.56". */
    val displayAmount: String,
    /** Human-readable message for the agent or assistant. */
    val message: String? = null
) {
    companion object {
        fun failed(message: String): FinancialAmountResponse = FinancialAmountResponse(
            status = STATUS_FAILED,
            amountMinorUnits = 0L,
            displayAmount = formatMinorUnits(0L),
            message = message
        )

        const val STATUS_SUCCESS = "success"
        const val STATUS_FAILED = "failed"
    }
}

internal fun CategoryListResult.toCategoryListResponse(): CategoryListResponse {
    return CategoryListResponse(
        status = status,
        message = message,
        categoriesJson = categories.toCategoryJson()
    )
}

private fun List<it.danielebufarini.spesify.data.AgentCategory>.toCategoryJson(): String {
    return joinToString(prefix = "[", separator = ",", postfix = "]") { category ->
        "{" +
            "\"id\":\"${category.id.escapeJson()}\"," +
            "\"name\":\"${category.name.escapeJson()}\"," +
            "\"kind\":\"${category.kind.toJsonKind()}\"," +
            "\"icon\":\"${category.icon.escapeJson()}\"," +
            "\"color\":\"${category.color.escapeJson()}\"," +
            "\"isArchived\":${category.isArchived}," +
            "\"isInUse\":${category.isInUse}" +
            "}"
    }
}

private fun TransactionKind.toJsonKind(): String {
    return when (this) {
        TransactionKind.Expense -> "expense"
        TransactionKind.Income -> "income"
    }
}

private fun String.escapeJson(): String {
    return buildString {
        for (char in this@escapeJson) {
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }
}

internal fun CategoryCommandResult.toCategoryMutationResponse(): CategoryMutationResponse {
    return when (this) {
        is CategoryCommandResult.Success -> CategoryMutationResponse(
            status = CategoryMutationResponse.STATUS_SUCCESS,
            categoryId = categoryId,
            message = message,
            needsConfirmation = false
        )
        is CategoryCommandResult.NeedsConfirmation -> CategoryMutationResponse(
            status = CategoryMutationResponse.STATUS_NEEDS_CONFIRMATION,
            message = message,
            needsConfirmation = true
        )
        is CategoryCommandResult.Failed -> CategoryMutationResponse(
            status = CategoryMutationResponse.STATUS_FAILED,
            message = message,
            needsConfirmation = false
        )
    }
}

internal fun AddTransactionResult.toAppFunctionResponse(): AddTransactionResponse {
    return when (this) {
        is AddTransactionResult.Created -> AddTransactionResponse(
            status = AddTransactionResponse.STATUS_CREATED,
            transactionId = transactionId,
            message = "Transaction added.",
            needsConfirmation = false
        )
        is AddTransactionResult.NeedsConfirmation -> AddTransactionResponse(
            status = AddTransactionResponse.STATUS_NEEDS_CONFIRMATION,
            message = message,
            needsConfirmation = true
        )
        is AddTransactionResult.Failed -> AddTransactionResponse(
            status = AddTransactionResponse.STATUS_FAILED,
            message = message,
            needsConfirmation = false
        )
    }
}

internal fun FinancialQueryResult.toFinancialAmountResponse(
    successMessagePrefix: String
): FinancialAmountResponse {
    if (!isSuccess) {
        return FinancialAmountResponse.failed(message ?: "Unable to read the requested amount.")
    }
    val displayAmount = formatMinorUnits(amount)
    return FinancialAmountResponse(
        status = FinancialAmountResponse.STATUS_SUCCESS,
        amountMinorUnits = amount,
        displayAmount = displayAmount,
        message = "$successMessagePrefix: $displayAmount."
    )
}

private fun formatMinorUnits(amountMinorUnits: Long): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale.getDefault())
    val amount = BigDecimal.valueOf(amountMinorUnits).movePointLeft(2)
    return formatter.format(amount)
}
