package it.danielebufarini.spesify.appfunctions

import androidx.appfunctions.AppFunctionSerializable

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
