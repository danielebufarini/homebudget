package it.danielebufarini.spesify.appfunctions

import it.danielebufarini.spesify.data.AddTransactionResult
import it.danielebufarini.spesify.data.CategoryCommandResult
import it.danielebufarini.spesify.data.CategoryListResult
import it.danielebufarini.spesify.data.FinancialQueryResult
import it.danielebufarini.spesify.data.TransactionKind
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

internal fun CategoryListResult.toCategoryListResponse(): CategoryListResponse {
    return CategoryListResponse(
        status = status,
        message = message,
        categoriesJson = categories.toCategoryJson()
    )
}

internal fun List<it.danielebufarini.spesify.data.AgentCategory>.toCategoryJson(): String {
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

internal fun TransactionKind.toJsonKind(): String {
    return when (this) {
        TransactionKind.Expense -> "expense"
        TransactionKind.Income -> "income"
    }
}

internal fun String.escapeJson(): String {
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

internal fun formatMinorUnits(amountMinorUnits: Long): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale.getDefault())
    val amount = BigDecimal.valueOf(amountMinorUnits).movePointLeft(2)
    return formatter.format(amount)
}
